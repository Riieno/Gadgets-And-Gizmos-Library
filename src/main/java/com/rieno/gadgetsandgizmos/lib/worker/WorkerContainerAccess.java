package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

// Resolve every distinct NeoForge storage capability a worker may access at one loaded block
public final class WorkerContainerAccess {
    // Initialize worker container access
    private WorkerContainerAccess() {
    }

    // Get all item handlers, preferring the linked face without limiting worker access to it
    public static List<IItemHandler> itemHandlers(Level level, BlockPos pos, @Nullable Direction preferredSide) {
        if (level == null || pos == null || !level.isLoaded(pos)) return List.of();
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<IItemHandler> handlers = new ArrayList<>();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        add(handlers, seen, preferredSide == null ? null : level.getCapability(
                Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, preferredSide));
        add(handlers, seen, level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, null));
        for (Direction side : Direction.values()) {
            if (side == preferredSide) continue;
            add(handlers, seen, level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, side));
        }
        return List.copyOf(handlers);
    }

    // Get all fluid handlers, preferring the linked face without limiting worker access to it
    public static List<IFluidHandler> fluidHandlers(Level level, BlockPos pos, @Nullable Direction preferredSide) {
        if (level == null || pos == null || !level.isLoaded(pos)) return List.of();
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<IFluidHandler> handlers = new ArrayList<>();
        Set<IFluidHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        add(handlers, seen, preferredSide == null ? null : level.getCapability(
                Capabilities.FluidHandler.BLOCK, pos, state, blockEntity, preferredSide));
        add(handlers, seen, level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, blockEntity, null));
        for (Direction side : Direction.values()) {
            if (side == preferredSide) continue;
            add(handlers, seen, level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, blockEntity, side));
        }
        return List.copyOf(handlers);
    }

    // Get all FE storages, preferring the linked face without limiting worker access to it
    public static List<IEnergyStorage> energyStorages(Level level, BlockPos pos, @Nullable Direction preferredSide) {
        if (level == null || pos == null || !level.isLoaded(pos)) return List.of();
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<IEnergyStorage> storages = new ArrayList<>();
        Set<IEnergyStorage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        add(storages, seen, preferredSide == null ? null : level.getCapability(
                Capabilities.EnergyStorage.BLOCK, pos, state, blockEntity, preferredSide));
        add(storages, seen, level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, state, blockEntity, null));
        for (Direction side : Direction.values()) {
            if (side == preferredSide) continue;
            add(storages, seen, level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, state, blockEntity, side));
        }
        return List.copyOf(storages);
    }

    // Add one non-duplicated handler
    private static <T> void add(List<T> handlers, Set<T> seen, @Nullable T handler) {
        if (handler != null && seen.add(handler)) handlers.add(handler);
    }

}
