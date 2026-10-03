package com.rieno.gadgetsandgizmos.lib.worker;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.function.Predicate;

// Reserve a bounded amount for one processing station before filling the next station
public record WorkerFluidPort(IFluidHandler inventory, Predicate<FluidStack> accepts, int maximum) implements IFluidHandler{
    @Override public int getTanks(){ return inventory.getTanks(); }
    @Override public FluidStack getFluidInTank(int tank){ return inventory.getFluidInTank(tank); }
    @Override public int getTankCapacity(int tank){ return Math.min(maximum, inventory.getTankCapacity(tank)); }
    @Override public boolean isFluidValid(int tank, FluidStack stack){ return accepts.test(stack) && inventory.isFluidValid(tank, stack); }
    @Override public int fill(FluidStack stack, FluidAction action){
        if(stack.isEmpty() || !accepts.test(stack)) return 0;
        long stocked = 0L;
        for(int idx = 0; idx < getTanks(); idx++){
            FluidStack existing = getFluidInTank(idx);
            if(FluidStack.isSameFluidSameComponents(existing, stack)) stocked += existing.getAmount();
        }
        int offered = (int)Math.min(stack.getAmount(), Math.max(0L, maximum - stocked));
        return offered == 0 ? 0 : inventory.fill(stack.copyWithAmount(offered), action);
    }
    @Override public FluidStack drain(FluidStack stack, FluidAction action){ return inventory.drain(stack, action); }
    @Override public FluidStack drain(int amount, FluidAction action){ return inventory.drain(amount, action); }
}
