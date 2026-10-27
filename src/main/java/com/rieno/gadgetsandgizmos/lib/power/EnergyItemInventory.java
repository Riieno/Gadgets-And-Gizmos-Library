package com.rieno.gadgetsandgizmos.lib.power;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

// Two rows of persistent charging slots: discharge the first row, charge the second
public final class EnergyItemInventory extends ItemStackHandler{
    private final Runnable changed;

    public EnergyItemInventory(Runnable changed){
        super(18);
        this.changed = changed;
    }

    @Override public int getSlotLimit(int slot){ return 1; }

    @Override public boolean isItemValid(int slot, ItemStack stack){
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy != null && (slot < 9 ? energy.canExtract() : energy.canReceive());
    }

    @Override protected void onContentsChanged(int slot){ changed.run(); }

    public long tick(IEnergyStorage storage){
        long moved = 0;
        for(int slot = 0; slot < getSlots(); slot++){
            IEnergyStorage item = getStackInSlot(slot).getCapability(Capabilities.EnergyStorage.ITEM);
            if(item == null) continue;
            long amount = slot < 9 ? EnergyTransfer.transfer(List.of(item), List.of(storage))
                    : EnergyTransfer.transfer(List.of(storage), List.of(item));
            if(amount > 0){ moved += amount; onContentsChanged(slot); }
        }
        return moved;
    }
}
