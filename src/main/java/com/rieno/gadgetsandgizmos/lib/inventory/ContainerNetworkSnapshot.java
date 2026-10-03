package com.rieno.gadgetsandgizmos.lib.inventory;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.power.LongEnergyStorage;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Page live registry-resource totals across loaded storage without truncating large inventories
public final class ContainerNetworkSnapshot{
    private ContainerNetworkSnapshot(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Call on the server thread with containers the viewer is allowed to inspect
    public static CompoundTag capture(Collection<? extends BlockEntity> targets, int offset, int limit){
        Map<String, CompoundTag> resources = new LinkedHashMap<>();
        Set<Storage> storages = new HashSet<>();
        Set<Object> itemHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> fluidHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> energyHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        int containers = 0;
        for(BlockEntity target : targets){
            if(target == null || !WorkerContainerAccess.isLoaded(target.getLevel(), target.getBlockPos())) continue;
            Level level = target.getLevel();
            BlockPos pos = ContainerStorageIdentity.position(level, target.getBlockPos());
            if(!WorkerContainerAccess.isLoaded(level, pos)) continue;
            if(!storages.add(new Storage(level, pos))) continue;
            BlockEntity root = level.getBlockEntity(pos);
            if(root == null) continue;
            var items = WorkerContainerAccess.itemHandlers(level, pos, null);
            var combinedFluid = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
            var fluids = combinedFluid == null ? WorkerContainerAccess.fluidHandlers(level, pos, null) : List.of(combinedFluid);
            var combinedEnergy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
            var energy = combinedEnergy == null ? WorkerContainerAccess.energyStorages(level, pos, null) : List.of(combinedEnergy);
            if(items.isEmpty() && fluids.isEmpty() && energy.isEmpty() && !(root instanceof LongEnergyStorage)) continue;
            containers++;
            for(var handler : items){
                if(!itemHandlers.add(handler)) continue;
                for(int slot = 0; slot < handler.getSlots(); slot++){
                    var stack = handler.getStackInSlot(slot);
                    if(stack.isEmpty()) continue;
                    add(resources, "item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                            stack.getItem().getDescription().getString(), stack.getCount(), 0);
                }
            }
            for(var handler : fluids){
                if(!fluidHandlers.add(handler)) continue;
                for(int tank = 0; tank < handler.getTanks(); tank++){
                    var stack = handler.getFluidInTank(tank);
                    add(resources, "fluid", BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString(),
                            stack.isEmpty() ? "Empty tanks" : stack.getHoverName().getString(),
                            stack.getAmount(), handler.getTankCapacity(tank));
                }
            }
            if(root instanceof LongEnergyStorage storage){
                add(resources, "energy", "neoforge:energy", "Energy", storage.getEnergyStored(), storage.getMaxEnergyStored());
            }else for(var handler : energy){
                if(energyHandlers.add(handler)) add(resources, "energy", "neoforge:energy", "Energy", handler.getEnergyStored(), handler.getMaxEnergyStored());
            }
        }
        var sorted = resources.values().stream().sorted(Comparator
                .comparingInt((CompoundTag row) -> switch(row.getString("Type")){ case "item" -> 0; case "fluid" -> 1; default -> 2; })
                .thenComparing(row -> row.getString("Name"), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> row.getString("Id"))).toList();
        int start = Math.clamp(offset, 0, Math.max(0, sorted.size() - 1));
        int end = Math.min(sorted.size(), start + Math.clamp(limit, 1, 64));
        ListTag rows = new ListTag();
        for(int idx = start; idx < end; idx++) rows.add(sorted.get(idx));
        CompoundTag data = new CompoundTag();
        data.putInt("Containers", containers);
        data.putInt("Total", sorted.size());
        data.putInt("Offset", start);
        data.put("Rows", rows);
        return data;
    }

    // Saturate totals instead of wrapping creative or long-capacity storage amounts
    private static void add(Map<String, CompoundTag> resources, String type, String id, String name, long amount, long capacity){
        CompoundTag row = resources.computeIfAbsent(type + ":" + id, key -> {
            CompoundTag res = new CompoundTag();
            res.putString("Type", type);
            res.putString("Id", id);
            res.putString("Name", name.substring(0, Math.min(128, name.length())));
            return res;
        });
        row.putLong("Amount", sum(row.getLong("Amount"), amount));
        row.putLong("Capacity", sum(row.getLong("Capacity"), capacity));
    }

    private static long sum(long prev, long val){ return prev + Math.min(Long.MAX_VALUE - prev, Math.max(0, val)); }
    private record Storage(Level level, BlockPos pos){}
}
