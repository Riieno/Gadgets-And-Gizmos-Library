package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.inventory.CombinedFluidTank;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerDataSource;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageIdentity;
import com.rieno.gadgetsandgizmos.lib.inventory.LocalResourceStorage;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.inventory.SharedCapacityFluidTank;
import com.rieno.gadgetsandgizmos.lib.power.LongEnergyStorage;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Read physical container totals without serializing NBT or scanning adjacent blocks
public final class ContainerData{
    private static final Set<String> DATA_PORTS = Set.of("items", "item_count", "item_capacity", "item_fill", "item_types",
            "fluids", "fluid_levels", "fluid_fill_levels", "fluid_amount", "fluid_capacity", "fluid_fill", "fluid_types",
            "energy", "energy_capacity", "energy_fill");
    private ContainerData(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve proxy targets through their public storage contract
    public static List<BlockEntity> containers(BlockEntity target){
        return target instanceof ContainerDataSource source ? source.dataContainers()
                : target == null ? List.of() : List.of(target);
    }

    // Recognize resource fields without resolving storage capabilities
    public static boolean isDataPort(String port){ return port != null && DATA_PORTS.contains(port); }

    // Inspect capability presence without reading inventory contents
    public static Map<String, String> readableData(BlockEntity target){
        if(target instanceof ContainerDataSource) return ports(true, true, true);
        boolean items = false, fluids = false, energy = false;
        for(var storage : storages(containers(target), null)){
            items |= !storage.pool().items().isEmpty();
            fluids |= !storage.pool().fluids().isEmpty();
            energy |= storage.owner() instanceof LongEnergyStorage || !storage.pool().energy().isEmpty();
        }
        return ports(items, fluids, energy);
    }

    // Refresh saved schemas when telemetry types or available resource categories change
    public static long schemaRevision(BlockEntity target){
        var ports = readableData(target);
        return ports.isEmpty() ? 0L : 31L + ports.hashCode();
    }

    // Capture one live reading on the owning game thread
    public static Snapshot capture(BlockEntity target, Direction side){
        if(target instanceof ContainerDataSource source) return capture(source);
        return capture(containers(target), side);
    }

    // Keep network and proxy schemas stable when members are unavailable or only known to the server
    public static Snapshot capture(ContainerDataSource source){
        var data = capture(source.dataContainers(), source.dataSide());
        return new Snapshot(data.items(), data.itemCount(), data.itemCapacity(), data.fluids(), data.fluidAmount(),
                data.fluidCapacity(), data.energy(), data.energyCapacity(), true, true, true);
    }

    // Aggregate each physical multiblock and handler once, retaining long storage totals
    public static Snapshot capture(Collection<? extends BlockEntity> targets, Direction side){
        Map<String, Long> items = new LinkedHashMap<>();
        Map<String, Long> fluids = new LinkedHashMap<>();
        Set<Object> seenItems = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> seenFluids = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> seenEnergy = Collections.newSetFromMap(new IdentityHashMap<>());
        long itemCount = 0, itemCapacity = 0, fluidAmount = 0, fluidCapacity = 0, energy = 0, energyCapacity = 0;
        boolean hasItems = false, hasFluids = false, hasEnergy = false;
        for(var storage : storages(targets, side)){
            var pool = storage.pool();
            hasItems |= !pool.items().isEmpty();
            hasFluids |= !pool.fluids().isEmpty();
            hasEnergy |= storage.owner() instanceof LongEnergyStorage || !pool.energy().isEmpty();
            if(storage.owner().getLevel().isClientSide) continue;
            for(var handler : pool.items()){
                if(!seenItems.add(handler)) continue;
                int slots = handler.getSlots();
                for(int idx = 0; idx < slots; idx++){
                    itemCapacity = sum(itemCapacity, handler.getSlotLimit(idx));
                    var stack = handler.getStackInSlot(idx);
                    if(stack.isEmpty()) continue;
                    long count = Math.max(0, stack.getCount());
                    itemCount = sum(itemCount, count);
                    items.merge(stack.getHoverName().getString(), count, ContainerData::sum);
                }
            }
            for(var handler : pool.fluids()){
                if(!seenFluids.add(handler)) continue;
                boolean shared = handler instanceof SharedCapacityFluidTank || handler instanceof CombinedFluidTank;
                if(shared) fluidCapacity = sum(fluidCapacity,
                        ((net.neoforged.neoforge.fluids.capability.templates.FluidTank)handler).getCapacity());
                int tanks = handler.getTanks();
                for(int idx = 0; idx < tanks; idx++){
                    if(!shared) fluidCapacity = sum(fluidCapacity, handler.getTankCapacity(idx));
                    var stack = handler.getFluidInTank(idx);
                    if(stack.isEmpty()) continue;
                    long amount = Math.max(0, stack.getAmount());
                    fluidAmount = sum(fluidAmount, amount);
                    fluids.merge(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString(), amount, ContainerData::sum);
                }
            }
            if(storage.owner() instanceof LongEnergyStorage exact){
                if(!seenEnergy.add(exact)) continue;
                energy = sum(energy, exact.getEnergyStored());
                energyCapacity = sum(energyCapacity, exact.getMaxEnergyStored());
            }else for(var handler : pool.energy()){
                if(!seenEnergy.add(handler)) continue;
                energy = sum(energy, handler.getEnergyStored());
                energyCapacity = sum(energyCapacity, handler.getMaxEnergyStored());
            }
        }
        return new Snapshot(items, itemCount, itemCapacity, fluids, fluidAmount, fluidCapacity,
                energy, energyCapacity, hasItems, hasFluids, hasEnergy);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Prefer the complete unsided inventory; face wrappers may overlap the same slots
    public static ResourceRouting.Pool resources(BlockEntity target, Direction side){
        if(target == null || target.isRemoved() || !WorkerContainerAccess.isLoaded(target.getLevel(), target.getBlockPos())){
            return ResourceRouting.Pool.EMPTY;
        }
        if(target instanceof LocalResourceStorage storage) return storage.localResources();
        return new ResourceRouting.Pool(capability(target, side, Capabilities.ItemHandler.BLOCK),
                capability(target, side, Capabilities.FluidHandler.BLOCK),
                capability(target, side, Capabilities.EnergyStorage.BLOCK));
    }

    private static <T> List<T> capability(BlockEntity target, Direction preferred, BlockCapability<T, Direction> capability){
        var level = target.getLevel();
        T handler = level.getCapability(capability, target.getBlockPos(), target.getBlockState(), target, null);
        if(handler == null && preferred != null){
            handler = level.getCapability(capability, target.getBlockPos(), target.getBlockState(), target, preferred);
        }
        if(handler == null) for(Direction side : Direction.values()){
            handler = level.getCapability(capability, target.getBlockPos(), target.getBlockState(), target, side);
            if(handler != null) break;
        }
        return handler == null ? List.of() : List.of(handler);
    }

    private static List<Storage> storages(Collection<? extends BlockEntity> targets, Direction side){
        List<Storage> res = new ArrayList<>();
        Set<Key> seen = new HashSet<>();
        for(var target : targets){
            if(target == null || target.isRemoved() || !WorkerContainerAccess.isLoaded(target.getLevel(), target.getBlockPos())) continue;
            var level = target.getLevel();
            var pos = ContainerStorageIdentity.position(level, target.getBlockPos());
            if(!WorkerContainerAccess.isLoaded(level, pos) || !seen.add(new Key(level, pos))) continue;
            var root = level.getBlockEntity(pos);
            if(root == null || root.isRemoved()) continue;
            res.add(new Storage(root, resources(root, side)));
        }
        return res;
    }

    private static Map<String, String> ports(boolean items, boolean fluids, boolean energy){
        Map<String, String> res = new LinkedHashMap<>();
        if(items){
            res.put("items", "map");
            for(String port : List.of("item_count", "item_capacity", "item_fill", "item_types")) res.put(port, "number");
        }
        if(fluids){
            res.put("fluids", "list");
            res.put("fluid_levels", "map");
            res.put("fluid_fill_levels", "map");
            for(String port : List.of("fluid_amount", "fluid_capacity", "fluid_fill", "fluid_types")) res.put(port, "number");
        }
        if(energy) for(String port : List.of("energy", "energy_capacity", "energy_fill")) res.put(port, "number");
        return Collections.unmodifiableMap(res);
    }

    private static long sum(long prev, long val){ return prev + Math.min(Long.MAX_VALUE - prev, Math.max(0L, val)); }
    private static double fill(long amount, long capacity){ return capacity <= 0 ? 0D : Math.min(1D, (double)amount / capacity); }
    private record Key(Level level, BlockPos pos){}
    private record Storage(BlockEntity owner, ResourceRouting.Pool pool){}

    // Keep telemetry immutable and independent of inventory stacks and NBT
    public record Snapshot(Map<String, Long> items, long itemCount, long itemCapacity,
            Map<String, Long> fluids, long fluidAmount, long fluidCapacity, long energy, long energyCapacity,
            boolean hasItems, boolean hasFluids, boolean hasEnergy){
        public Snapshot{
            items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
            fluids = Collections.unmodifiableMap(new LinkedHashMap<>(fluids));
        }

        public Map<String, String> readableData(){ return ports(hasItems, hasFluids, hasEnergy); }

        // Return typed graph values so collection breakouts retain numeric counts
        public GraphValue read(String port){
            return switch(port){
                case "items" -> GraphValue.map(items);
                case "item_count" -> GraphValue.number(itemCount);
                case "item_capacity" -> GraphValue.number(itemCapacity);
                case "item_fill" -> GraphValue.number(fill(itemCount, itemCapacity));
                case "item_types" -> GraphValue.number(items.size());
                case "fluids" -> GraphValue.list(fluids.entrySet().stream().map(entry -> entry.getKey() + " x" + entry.getValue()).toList());
                case "fluid_levels" -> GraphValue.map(fluids);
                case "fluid_fill_levels" -> {
                    Map<String, Double> levels = new LinkedHashMap<>();
                    fluids.forEach((id, amount) -> levels.put(id, fill(amount, fluidCapacity)));
                    yield GraphValue.map(levels);
                }
                case "fluid_amount" -> GraphValue.number(fluidAmount);
                case "fluid_capacity" -> GraphValue.number(fluidCapacity);
                case "fluid_fill" -> GraphValue.number(fill(fluidAmount, fluidCapacity));
                case "fluid_types" -> GraphValue.number(fluids.size());
                case "energy" -> GraphValue.number(energy);
                case "energy_capacity" -> GraphValue.number(energyCapacity);
                case "energy_fill" -> GraphValue.number(fill(energy, energyCapacity));
                default -> null;
            };
        }

        // Export the same values to scripts without exposing graph implementation types
        public Map<String, Object> values(){
            Map<String, Object> res = new LinkedHashMap<>();
            readableData().keySet().forEach(port -> res.put(port, read(port).value()));
            return Collections.unmodifiableMap(res);
        }
    }
}
