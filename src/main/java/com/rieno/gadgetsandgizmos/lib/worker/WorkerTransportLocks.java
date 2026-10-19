package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Lease transport gates to one worker order without changing saved block states
public final class WorkerTransportLocks{
    private static final long LEASE_TICKS = 40L;
    private static final Map<Level, Map<BlockPos, Lease>> LOCKS = new WeakHashMap<>();

    private WorkerTransportLocks(){}

    // Acquire every gate together so a competing job cannot receive a partial route
    public static synchronized boolean acquire(Level level, UUID owner, List<BlockPos> positions){
        if(level == null || owner == null || positions == null) return false;
        if(positions.isEmpty()) return true;
        Map<BlockPos, Lease> locks = LOCKS.computeIfAbsent(level, ignored -> new HashMap<>());
        long now = level.getGameTime();
        locks.entrySet().removeIf(entry -> entry.getValue().expires() <= now);
        for(BlockPos pos : positions){
            if(pos == null || !WorkerContainerAccess.isLoaded(level, pos)) return false;
            Lease held = locks.get(pos);
            if(held != null && !held.owner().equals(owner)) return false;
        }
        for(BlockPos pos : positions) locks.put(pos.immutable(), new Lease(owner, now + LEASE_TICKS));
        return true;
    }

    // Check a live gate from a transport adapter or mixin
    public static synchronized boolean isLocked(Level level, BlockPos pos){
        if(level == null || pos == null) return false;
        Map<BlockPos, Lease> locks = LOCKS.get(level);
        if(locks == null) return false;
        Lease held = locks.get(pos);
        if(held == null) return false;
        if(held.expires() > level.getGameTime()) return true;
        locks.remove(pos);
        return false;
    }

    // Release every gate owned by a completed or cancelled order
    public static synchronized void release(Level level, UUID owner){
        if(level == null || owner == null) return;
        Map<BlockPos, Lease> locks = LOCKS.get(level);
        if(locks != null) locks.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
    }

    public static synchronized void releaseOwner(UUID owner){
        if(owner == null) return;
        for(Map<BlockPos, Lease> locks : LOCKS.values())
            locks.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
    }

    private record Lease(UUID owner, long expires){}
}
