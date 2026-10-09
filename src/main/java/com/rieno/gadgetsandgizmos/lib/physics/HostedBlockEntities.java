package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

// Publish detached components for integrations which explicitly opt into hosted lookups
public final class HostedBlockEntities{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<BlockEntity, Map<BlockPos, BlockEntity>> COMPONENTS = new IdentityHashMap<>();

    private HostedBlockEntities(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Replace one host's components without placing blocks or changing world lookups
    public static synchronized void publish(BlockEntity host, Collection<? extends BlockEntity> components){
        Objects.requireNonNull(host, "host");
        Level level = Objects.requireNonNull(host.getLevel(), "host level");
        Map<BlockPos, BlockEntity> next = new java.util.LinkedHashMap<>();
        for(BlockEntity component : components){
            if(component == host || component.isRemoved() || component.getLevel() != level
                    || Objects.equals(component.getBlockPos(), host.getBlockPos())
                    || level.getBlockEntity(component.getBlockPos()) != null){
                throw new IllegalArgumentException("A hosted component must be detached and share its host level");
            }
            if(next.putIfAbsent(component.getBlockPos(), component) != null){
                throw new IllegalArgumentException("Duplicate hosted component position");
            }
            for(var entry : COMPONENTS.entrySet()){
                if(entry.getKey() != host && entry.getKey().getLevel() == level
                        && !entry.getKey().isRemoved() && entry.getValue().containsKey(component.getBlockPos())){
                    throw new IllegalStateException("Another host owns this component position");
                }
            }
        }
        COMPONENTS.put(host, Map.copyOf(next));
    }

    // Resolve a component only for an integration's native lookup path
    public static synchronized @Nullable BlockEntity resolve(Level level, BlockPos pos){
        for(var entry : COMPONENTS.entrySet()){
            BlockEntity host = entry.getKey();
            if(host.isRemoved() || host.getLevel() != level) continue;
            BlockEntity component = entry.getValue().get(pos);
            if(component != null && !component.isRemoved() && level.getBlockEntity(pos) == null) return component;
        }
        return null;
    }

    // Find the real block which owns one detached component
    public static synchronized @Nullable BlockEntity host(BlockEntity component){
        for(var entry : COMPONENTS.entrySet()){
            if(!entry.getKey().isRemoved() && entry.getValue().containsValue(component)) return entry.getKey();
        }
        return null;
    }

    // Release all published components during removal, unload or relocation
    public static synchronized void remove(BlockEntity host){ COMPONENTS.remove(host); }

    // Check whether a detached identity can be published without hiding a real block entity
    public static synchronized boolean positionAvailable(BlockEntity host, BlockPos pos){
        Level level = host.getLevel();
        if(level == null || pos.equals(host.getBlockPos()) || level.getBlockEntity(pos) != null) return false;
        return resolve(level, pos) == null;
    }

    // Assign stable nearby identities above or below a host at the world height limit
    public static BlockPos positionForSlot(BlockEntity host, int slot, int width){
        if(slot < 1 || width < 1) throw new IllegalArgumentException("Invalid hosted position slot");
        Level level = Objects.requireNonNull(host.getLevel());
        int row = Math.addExact(slot / width, 1);
        int offset = (long) host.getBlockPos().getY() + row >= level.getMaxBuildHeight() ? -row : row;
        return host.getBlockPos().offset(slot % width, offset, 0);
    }

    // Skip world blocks, occupied identities and positions outside the level's height
    public static int findAvailableSlot(BlockEntity host, int firstSlot, int width, Collection<BlockPos> reserved){
        var occupied = java.util.Set.copyOf(reserved);
        Level level = Objects.requireNonNull(host.getLevel());
        for(int idx = 0; idx < 16384; idx++){
            int slot = Math.addExact(firstSlot, idx);
            BlockPos pos = positionForSlot(host, slot, width);
            if(pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) continue;
            if(!occupied.contains(pos) && positionAvailable(host, pos)) return slot;
        }
        throw new IllegalStateException("No free hosted component position");
    }

    // Read one host's stable component snapshot
    public static synchronized java.util.List<BlockEntity> components(BlockEntity host){
        return java.util.List.copyOf(COMPONENTS.getOrDefault(host, Map.of()).values());
    }

    // Keep a native component's visual state in its host instead of placing a world block
    public static boolean updateState(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state){
        BlockEntity component = resolve(level, pos);
        if(component == null) return false;
        component.setBlockState(Objects.requireNonNull(state));
        notifyHost(component, true);
        return true;
    }

    // Forward persistence and synchronization to the real host on its game thread
    public static boolean notifyHost(BlockEntity component, boolean sync){
        return notifyHost(component, sync, false);
    }

    // Forward a detached visual update without invalidating the host's chunk mesh
    public static boolean notifyHostData(BlockEntity component, boolean sync){
        return notifyHost(component, sync, true);
    }

    private static boolean notifyHost(BlockEntity component, boolean sync, boolean dataOnly){
        BlockEntity owner = null;
        synchronized(HostedBlockEntities.class){
            for(var entry : COMPONENTS.entrySet()){
                if(entry.getValue().containsValue(component)){ owner = entry.getKey(); break; }
            }
        }
        if(owner == null) return false;
        if(owner.isRemoved()) return true;
        BlockEntity host = owner;
        Runnable action = () -> {
            if(host.isRemoved()) return;
            host.setChanged();
            if(sync && dataOnly) com.rieno.gadgetsandgizmos.lib.network.BlockEntityDataSync.enqueue(host);
            else if(sync && host instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart) smart.sendData();
        };
        if(owner.getLevel() != null && !owner.getLevel().isClientSide && owner.getLevel().getServer() != null
                && !owner.getLevel().getServer().isSameThread()) owner.getLevel().getServer().execute(action);
        else action.run();
        return true;
    }

    // Tick a stable component snapshot through each component's native Sable callback
    public static void physicsTick(BlockEntity host, dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel,
            dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle, double dt,
            java.util.function.Predicate<BlockEntity> active){
        for(BlockEntity component : components(host)){
            if(!component.isRemoved() && active.test(component)
                    && component instanceof dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor actor){
                actor.sable$physicsTick(subLevel, handle, dt);
            }
        }
    }
}
