package com.rieno.gadgetsandgizmos.lib.inventory;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.rieno.gadgetsandgizmos.lib.power.LongEnergyStorage;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

// Capture bounded, detailed cargo rows without exposing mutable inventory stacks
public final class ContainerContentsSnapshot{
    private ContainerContentsSnapshot(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Group equal components while retaining slot, capacity and fluid details
    public static CompoundTag capture(BlockEntity target){
        CompoundTag data = new CompoundTag();
        data.putBoolean("Attached", true);
        data.putString("Name", target.getBlockState().getBlock().getName().getString());
        var level = target.getLevel();
        var pos = target.getBlockPos();
        var itemHandlers = WorkerContainerAccess.itemHandlers(level, pos, null);
        var fluidHandlers = WorkerContainerAccess.fluidHandlers(level, pos, null);
        var energyStorages = WorkerContainerAccess.energyStorages(level, pos, null);
        IItemHandler inventory = itemHandlers.isEmpty() ? null : itemHandlers.getFirst();
        IFluidHandler fluids = fluidHandlers.isEmpty() ? null : fluidHandlers.getFirst();
        var energy = energyStorages.isEmpty() ? null : energyStorages.getFirst();
        LongEnergyStorage longEnergy = target instanceof LongEnergyStorage storage ? storage : null;
        if(inventory == null && fluids == null && energy == null && longEnergy == null) throw new IllegalArgumentException("This target has no container capability");
        ListTag rows = new ListTag();
        if(inventory != null){
            if(inventory.getSlots() > 4096) throw new IllegalArgumentException("Container exceeds the snapshot slot limit");
            int occupied = 0;
            long capacity = 0;
            long total = 0;
            int omitted = 0;
            for(int idx = 0; idx < inventory.getSlots(); idx++){
                ItemStack stack = inventory.getStackInSlot(idx);
                capacity += inventory.getSlotLimit(idx);
                if(stack.isEmpty()) continue;
                occupied++;
                total += stack.getCount();
                CompoundTag row = null;
                for(int rowIdx = 0; rowIdx < rows.size(); rowIdx++){
                    var existing = rows.getCompound(rowIdx);
                    var item = ItemStack.parseOptional(level.registryAccess(), existing.getCompound("Item"));
                    if(ItemStack.isSameItemSameComponents(item, stack)){ row = existing; break; }
                }
                if(row == null){
                    if(rows.size() >= 256){ omitted++; continue; }
                    row = new CompoundTag();
                    var sample = stack.copyWithCount(1).save(level.registryAccess());
                    if(sample.toString().length() > 2048){ omitted++; continue; }
                    row.put("Item", sample);
                    row.putString("Name", shortText(stack.getHoverName().getString(), 128));
                    row.putString("Id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    row.putString("Components", shortText(stack.getComponentsPatch().toString(), 512));
                    row.putInt("MaxStack", stack.getMaxStackSize());
                    rows.add(row);
                }
                row.putLong("Count", row.getLong("Count") + stack.getCount());
                row.putInt("Slots", row.getInt("Slots") + 1);
            }
            data.putInt("Slots", inventory.getSlots());
            data.putInt("Occupied", occupied);
            data.putLong("Capacity", capacity);
            data.putLong("ItemCount", total);
            data.putInt("OmittedSlots", omitted);
        }
        data.put("Items", rows);
        ListTag liquid = new ListTag();
        if(fluids != null) for(int idx = 0; idx < fluids.getTanks() && idx < 128; idx++){
            var stack = fluids.getFluidInTank(idx);
            CompoundTag row = new CompoundTag();
            row.putString("Name", stack.isEmpty() ? "Empty" : shortText(stack.getHoverName().getString(), 128));
            row.putString("Id", BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
            row.putInt("Amount", stack.getAmount());
            row.putInt("Capacity", fluids.getTankCapacity(idx));
            liquid.add(row);
        }
        data.put("Fluids", liquid);
        if(longEnergy != null){
            data.putLong("Energy", longEnergy.getEnergyStored());
            data.putLong("EnergyCapacity", longEnergy.getMaxEnergyStored());
        }else if(energy != null){
            data.putLong("Energy", energy.getEnergyStored());
            data.putLong("EnergyCapacity", energy.getMaxEnergyStored());
        }
        return data;
    }

    // Check every exposed face, including long-capacity FE stored directly on the block entity
    public static boolean canCapture(BlockEntity target){
        if(target == null || target.getLevel() == null) return false;
        var level = target.getLevel();
        var pos = target.getBlockPos();
        return target instanceof LongEnergyStorage
                || !WorkerContainerAccess.itemHandlers(level, pos, null).isEmpty()
                || !WorkerContainerAccess.fluidHandlers(level, pos, null).isEmpty()
                || !WorkerContainerAccess.energyStorages(level, pos, null).isEmpty();
    }

    private static String shortText(String val, int limit){ return val.length() <= limit ? val : val.substring(0, limit - 1) + "…"; }
}
