package com.rieno.gadgetsandgizmos.lib.inventory;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.function.Predicate;
import java.util.function.Supplier;

// Expose matching fluids and shared headroom independently of an ordinary automation output selection
public final class FilteredFluidHandler implements IFluidHandler{
    private final Supplier<IFluidHandler> storage;
    private final Predicate<FluidStack> allowed;
    private final boolean filterInsertion;

    public FilteredFluidHandler(Supplier<IFluidHandler> storage, Predicate<FluidStack> allowed){
        this(storage, allowed, true);
    }

    // Output-only views still accept other incoming fluids into the shared storage
    public FilteredFluidHandler(Supplier<IFluidHandler> storage, Predicate<FluidStack> allowed, boolean filterInsertion){
        this.storage = storage;
        this.allowed = allowed;
        this.filterInsertion = filterInsertion;
    }
    private java.util.List<Integer> visible(){
        var handler = storage.get();
        java.util.List<Integer> res = new java.util.ArrayList<>();
        for(int idx = 0; idx < handler.getTanks(); idx++){
            var stack = handler.getFluidInTank(idx);
            if(!stack.isEmpty() && allowed.test(stack)) res.add(idx);
        }
        return res;
    }
    @Override public int getTanks(){ return Math.max(1, visible().size()); }
    @Override public FluidStack getFluidInTank(int tank){
        var rows = visible();
        return tank < 0 || tank >= rows.size() ? FluidStack.EMPTY : storage.get().getFluidInTank(rows.get(tank));
    }
    @Override public int getTankCapacity(int tank){
        var handler = storage.get();
        var rows = visible();
        if(handler instanceof net.neoforged.neoforge.fluids.capability.templates.FluidTank shared){
            int amount = getFluidInTank(tank).getAmount();
            return (int)Math.min(Integer.MAX_VALUE, (long)amount + (tank == 0 ? shared.getSpace() : 0));
        }
        return rows.isEmpty() ? handler.getTankCapacity(0)
                : tank >= 0 && tank < rows.size() ? handler.getTankCapacity(rows.get(tank)) : 0;
    }
    @Override public boolean isFluidValid(int tank, FluidStack stack){
        return (!filterInsertion || allowed.test(stack)) && storage.get().isFluidValid(0, stack);
    }
    @Override public int fill(FluidStack stack, FluidAction action){
        return !filterInsertion || allowed.test(stack) ? storage.get().fill(stack, action) : 0;
    }
    @Override public FluidStack drain(FluidStack stack, FluidAction action){
        return allowed.test(stack) ? storage.get().drain(stack, action) : FluidStack.EMPTY;
    }
    @Override public FluidStack drain(int amount, FluidAction action){
        return ResourceRouting.extractFluid(new ResourceRouting.Pool(java.util.List.of(), java.util.List.of(storage.get()), java.util.List.of()),
                allowed, amount, action.simulate());
    }
}
