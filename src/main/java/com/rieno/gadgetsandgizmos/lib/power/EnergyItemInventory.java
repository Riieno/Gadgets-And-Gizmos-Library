package com.rieno.gadgetsandgizmos.lib.power;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

// Two rows of persistent charging slots: discharge the first row, charge the second
public final class EnergyItemInventory extends ItemStackHandler{
    private final Runnable changed;
    private final Function<ItemStack, IEnergyStorage> fallback;

    public EnergyItemInventory(Runnable changed){
        this(changed, stack -> null);
    }

    // Let hosts adapt optional item energy formats while retaining native FE capabilities first
    public EnergyItemInventory(Runnable changed, Function<ItemStack, IEnergyStorage> fallback){
        super(18);
        this.changed = changed;
        this.fallback = Objects.requireNonNull(fallback);
    }

    @Override public int getSlotLimit(int slot){ return 1; }

    @Override public boolean isItemValid(int slot, ItemStack stack){
        IEnergyStorage energy = energy(stack);
        return energy != null && (slot < 9 ? energy.canExtract() : energy.canReceive());
    }

    @Override protected void onContentsChanged(int slot){ changed.run(); }

    public long tick(IEnergyStorage storage){
        long moved = 0;
        for(int slot = 0; slot < getSlots(); slot++){
            IEnergyStorage item = energy(getStackInSlot(slot));
            if(item == null) continue;
            long amount = slot < 9 ? EnergyTransfer.transfer(List.of(item), List.of(storage))
                    : EnergyTransfer.transfer(List.of(storage), List.of(item));
            if(amount > 0){ moved += amount; onContentsChanged(slot); }
        }
        return moved;
    }

    private IEnergyStorage energy(ItemStack stack){
        if(stack.isEmpty()) return null;
        var nativeEnergy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return nativeEnergy != null ? nativeEnergy : fallback.apply(stack);
    }
}
