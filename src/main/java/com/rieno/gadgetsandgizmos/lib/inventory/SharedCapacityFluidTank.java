package com.rieno.gadgetsandgizmos.lib.inventory;

import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Store distinct fluid components in one shared capacity instead of reserving a full tank for every type
public class SharedCapacityFluidTank extends SmartFluidTank{
    private static final int MAX_FLUIDS = 128;
    private final List<FluidStack> contents = new ArrayList<>();
    private long revision;

    public SharedCapacityFluidTank(int capacity, Consumer<FluidStack> changed){ super(capacity, changed); }

    public long revision(){ return revision; }
    @Override protected void onContentsChanged(){ revision++; super.onContentsChanged(); }

    // Let physical storage owners meter executed transfers without counting NBT restoration
    protected void onTransfer(int input, int output){}

    @Override public int getTanks(){ return Math.max(1, contents.size()); }
    @Override public FluidStack getFluidInTank(int tank){
        return tank < 0 || tank >= contents.size() ? FluidStack.EMPTY : contents.get(tank);
    }
    @Override public int getTankCapacity(int tank){ return Math.min(getCapacity(), getFluidInTank(tank).getAmount() + getSpace()); }
    @Override public boolean isFluidValid(int tank, FluidStack stack){ return !stack.isEmpty(); }
    @Override public boolean isFluidValid(FluidStack stack){ return !stack.isEmpty(); }
    @Override public FluidStack getFluid(){ return getFluidInTank(0); }
    @Override public int getFluidAmount(){
        long amount = 0;
        for(var stack : contents) amount += stack.getAmount();
        return (int)Math.min(Integer.MAX_VALUE, amount);
    }
    @Override public boolean isEmpty(){ return contents.isEmpty(); }
    @Override public int getSpace(){ return Math.max(0, getCapacity() - getFluidAmount()); }

    @Override public int fill(FluidStack stack, FluidAction action){
        if(stack.isEmpty()) return 0;
        int idx = index(stack);
        if(idx < 0 && contents.size() >= MAX_FLUIDS) return 0;
        int accepted = Math.min(stack.getAmount(), getSpace());
        if(accepted > 0 && action.execute()){
            if(idx < 0) contents.add(stack.copyWithAmount(accepted));
            else contents.get(idx).grow(accepted);
            onContentsChanged();
            onTransfer(accepted, 0);
        }
        return accepted;
    }

    @Override public FluidStack drain(FluidStack stack, FluidAction action){
        if(stack.isEmpty()) return FluidStack.EMPTY;
        int idx = index(stack);
        if(idx < 0) return FluidStack.EMPTY;
        var stored = contents.get(idx);
        int amount = Math.min(stack.getAmount(), stored.getAmount());
        var res = stored.copyWithAmount(amount);
        if(amount > 0 && action.execute()){
            stored.shrink(amount);
            if(stored.isEmpty()) contents.remove(idx);
            onContentsChanged();
            onTransfer(0, amount);
        }
        return res;
    }

    @Override public FluidStack drain(int amount, FluidAction action){
        return amount <= 0 || contents.isEmpty() ? FluidStack.EMPTY : drain(contents.getFirst().copyWithAmount(amount), action);
    }

    // Capacity changes never silently destroy legacy contents while the owning multiblock is loading
    @Override public FluidTank setCapacity(int capacity){ return super.setCapacity(Math.max(0, capacity)); }

    @Override public void setFluid(FluidStack stack){
        contents.clear();
        if(!stack.isEmpty()) contents.add(stack.copy());
        onContentsChanged();
    }

    @Override public FluidTank readFromNBT(HolderLookup.Provider registries, CompoundTag tag){
        revision++;
        contents.clear();
        if(tag.contains("Fluids", Tag.TAG_LIST)){
            var rows = tag.getList("Fluids", Tag.TAG_COMPOUND);
            for(int idx = 0; idx < rows.size() && idx < MAX_FLUIDS; idx++){
                var stack = FluidStack.parseOptional(registries, rows.getCompound(idx));
                if(stack.isEmpty()) continue;
                int existing = index(stack);
                if(existing < 0) contents.add(stack);
                else contents.get(existing).setAmount((int)Math.min(Integer.MAX_VALUE,
                        (long)contents.get(existing).getAmount() + stack.getAmount()));
            }
        }else{
            var legacy = FluidStack.parseOptional(registries, tag.getCompound("Fluid"));
            if(!legacy.isEmpty()) contents.add(legacy);
        }
        return this;
    }

    @Override public CompoundTag writeToNBT(HolderLookup.Provider registries, CompoundTag tag){
        ListTag rows = new ListTag();
        for(var stack : contents) rows.add(stack.save(registries));
        tag.put("Fluids", rows);
        return tag;
    }

    // Restore an unaccepted remainder without truncating an over-capacity legacy import
    public void restore(FluidStack stack){
        if(stack.isEmpty()) return;
        int idx = index(stack);
        if(idx < 0) contents.add(stack.copy());
        else contents.get(idx).grow(stack.getAmount());
        onContentsChanged();
    }

    private int index(FluidStack stack){
        for(int idx = 0; idx < contents.size(); idx++){
            if(FluidStack.isSameFluidSameComponents(contents.get(idx), stack)) return idx;
        }
        return -1;
    }
}
