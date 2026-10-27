package com.rieno.gadgetsandgizmos.lib.client.render;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Size fluid layers by their actual contents while sharing the tank's animated fill level
public final class FluidTankLayerLayout{
    private FluidTankLayerLayout(){}

    // Heights are fractions of the usable tank interior; gases collect at its ceiling
    public record Layer(FluidStack fluid, double bottom, double top){}

    public static List<Layer> layout(IFluidHandler storage, long capacity, float fillFraction){
        if(storage == null || capacity <= 0 || !Float.isFinite(fillFraction) || fillFraction <= 0) return List.of();
        List<FluidStack> fluids = new ArrayList<>();
        long amount = 0;
        int tanks = storage.getTanks();
        for(int idx = 0; idx < tanks; idx++){
            var stack = storage.getFluidInTank(idx);
            if(stack.isEmpty()) continue;
            amount += stack.getAmount();
            fluids.add(stack.copy());
        }
        if(amount == 0) return List.of();
        fluids.sort(Comparator.comparing(FluidTankLayerLayout::gas)
                .thenComparing(Comparator.comparingInt((FluidStack stack) -> stack.getFluid().getFluidType().getDensity(stack)).reversed()));
        long gasAmount = 0;
        for(var stack : fluids) if(gas(stack)) gasAmount += stack.getAmount();
        double fill = Math.min(1D, fillFraction);
        double liquid = 0;
        double vapor = 1D - fill * gasAmount / amount;
        List<Layer> layers = new ArrayList<>(fluids.size());
        for(var stack : fluids){
            double height = fill * stack.getAmount() / amount;
            double bottom = gas(stack) ? vapor : liquid;
            double top = Math.min(1D, bottom + height);
            layers.add(new Layer(stack, bottom, top));
            if(gas(stack)) vapor = top;
            else liquid = top;
        }
        return List.copyOf(layers);
    }

    private static boolean gas(FluidStack stack){ return stack.getFluid().getFluidType().isLighterThanAir(); }
}
