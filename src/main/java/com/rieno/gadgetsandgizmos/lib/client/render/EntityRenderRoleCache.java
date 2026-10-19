package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

// Reuse one entity role during a world tick without retaining unloaded entities or levels
public final class EntityRenderRoleCache{
    private final Predicate<LivingEntity> query;
    private final Map<LivingEntity, Snapshot> snapshots = new WeakHashMap<>();

    // Supply the role check shared by every entity rendered through this cache
    public EntityRenderRoleCache(Predicate<LivingEntity> query){
        this.query = Objects.requireNonNull(query, "Role query is missing");
    }

    // Recheck after a tick, level, mount or seat-position change on the client thread
    public boolean get(LivingEntity rendered){
        if(rendered == null) return false;
        LivingEntity source = HeadwearRenderSource.resolve(rendered);
        if(source.isRemoved()){
            snapshots.remove(source);
            return false;
        }
        Level level = source.level();
        Entity vehicle = source.getVehicle();
        UUID vehicleId = vehicle == null ? null : vehicle.getUUID();
        BlockPos seatPos = vehicle == null ? null : vehicle.blockPosition();
        long tick = level.getGameTime();
        Snapshot prev = snapshots.get(source);
        if(prev != null && prev.level().get() == level && prev.tick() == tick
                && prev.age() == source.tickCount && Objects.equals(prev.vehicleId(), vehicleId)
                && Objects.equals(prev.seatPos(), seatPos)) return prev.role();
        boolean role = query.test(source);
        snapshots.put(source, new Snapshot(new WeakReference<>(level), tick, source.tickCount,
                vehicleId, seatPos == null ? null : seatPos.immutable(), role));
        return role;
    }

    // Discard one role after an explicit equipment or role update
    public void invalidate(LivingEntity rendered){
        if(rendered != null) snapshots.remove(HeadwearRenderSource.resolve(rendered));
    }

    // Release every role snapshot after a renderer or resource lifecycle change
    public void clear(){
        snapshots.clear();
    }

    private record Snapshot(WeakReference<Level> level, long tick, int age,
                            UUID vehicleId, BlockPos seatPos, boolean role){}
}
