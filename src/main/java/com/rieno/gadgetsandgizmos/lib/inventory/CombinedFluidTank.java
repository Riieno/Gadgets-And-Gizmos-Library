package com.rieno.gadgetsandgizmos.lib.inventory;

import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;
import java.util.function.Supplier;

// Present distributed member tanks to Create renderers and ordinary fluid consumers as one live tank
public final class CombinedFluidTank extends SmartFluidTank{
    private final Supplier<List<SharedCapacityFluidTank>> members;

    public CombinedFluidTank(Supplier<List<SharedCapacityFluidTank>> members){
        super(0, stack -> {});
        this.members = members;
    }

    private ResourceRouting.Pool pool(){ return new ResourceRouting.Pool(List.of(), List.copyOf(members.get()), List.of()); }
    private List<SharedCapacityFluidTank> cachedMembers = List.of();
    private long[] revisions = new long[0];
    private List<FluidStack> cachedContents = List.of();

    private List<FluidStack> contents(){
        var tanks = members.get();
        boolean changed = tanks.size() != cachedMembers.size();
        for(int idx = 0; !changed && idx < tanks.size(); idx++){
            changed = tanks.get(idx) != cachedMembers.get(idx) || tanks.get(idx).revision() != revisions[idx];
        }
        if(!changed) return cachedContents;
        java.util.List<FluidStack> rows = new java.util.ArrayList<>();
        revisions = new long[tanks.size()];
        for(int idx = 0; idx < tanks.size(); idx++){
            var tank = tanks.get(idx);
            revisions[idx] = tank.revision();
            for(int slot = 0; slot < tank.getTanks(); slot++){
                var stack = tank.getFluidInTank(slot);
                if(stack.isEmpty()) continue;
                var existing = rows.stream().filter(row -> FluidStack.isSameFluidSameComponents(row, stack)).findFirst().orElse(null);
                if(existing == null) rows.add(stack.copy());
                else existing.setAmount((int)Math.min(Integer.MAX_VALUE, (long)existing.getAmount() + stack.getAmount()));
            }
        }
        cachedMembers = List.copyOf(tanks);
        cachedContents = List.copyOf(rows);
        return cachedContents;
    }

    @Override public int getTanks(){ return Math.max(1, contents().size()); }
    @Override public FluidStack getFluidInTank(int index){
        var rows = contents();
        return index < 0 || index >= rows.size() ? FluidStack.EMPTY : rows.get(index);
    }
    @Override public int getTankCapacity(int index){
        return (int)Math.min(getCapacity(), (long)getFluidInTank(index).getAmount() + getSpace());
    }
    @Override public boolean isFluidValid(int tank, FluidStack stack){ return !stack.isEmpty(); }
    @Override public boolean isFluidValid(FluidStack stack){ return !stack.isEmpty(); }
    @Override public int fill(FluidStack stack, FluidAction action){
        return stack.isEmpty() ? 0 : ResourceRouting.insertFluid(pool(), stack.copyWithAmount(Math.min(stack.getAmount(), getSpace())), action.simulate());
    }
    @Override public FluidStack drain(FluidStack stack, FluidAction action){
        if(stack.isEmpty()) return FluidStack.EMPTY;
        return ResourceRouting.extractFluid(pool(), available -> FluidStack.isSameFluidSameComponents(stack, available), stack.getAmount(), action.simulate());
    }
    @Override public FluidStack drain(int amount, FluidAction action){ return ResourceRouting.extractFluid(pool(), stack -> true, amount, action.simulate()); }
    @Override public FluidStack getFluid(){ return getFluidInTank(0); }
    @Override public int getCapacity(){ return total(true); }
    @Override public int getFluidAmount(){ return total(false); }
    @Override public boolean isEmpty(){ return getFluidAmount() == 0; }
    @Override public int getSpace(){ return Math.max(0, getCapacity() - getFluidAmount()); }
    @Override public FluidTank setCapacity(int capacity){ return this; }
    @Override public void setFluid(FluidStack stack){
        for(var member : members.get()) member.setFluid(FluidStack.EMPTY);
        if(!stack.isEmpty()) fill(stack, FluidAction.EXECUTE);
    }

    private int total(boolean capacity){
        long res = 0;
        for(var member : members.get()) res += capacity ? member.getCapacity() : member.getFluidAmount();
        return (int)Math.min(Integer.MAX_VALUE, res);
    }
}
