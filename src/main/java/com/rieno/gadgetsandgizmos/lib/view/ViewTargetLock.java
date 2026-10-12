package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

// Select the nearest fresh hit or retain an acquired identity while target locking is enabled
public final class ViewTargetLock<K>{
    private K key;

    // Resolve a retained target before choosing the nearest eligible new hit
    public @Nullable Target<K> update(Vec3 origin, double range, Collection<Target<K>> hits,
                                     Function<K, Target<K>> resolver){
        return update(origin, range, () -> hits, resolver);
    }

    // Resolve candidate geometry only when the retained identity no longer qualifies
    public @Nullable Target<K> update(Vec3 origin, double range, Supplier<? extends Collection<Target<K>>> hits,
                                     Function<K, Target<K>> resolver){
        return update(origin, range, hits, resolver, true);
    }

    // Choose the closest fresh hit unless the caller explicitly retains its current target
    public @Nullable Target<K> update(Vec3 origin, double range, Collection<Target<K>> hits,
                                     Function<K, Target<K>> resolver, boolean lockTarget){
        return update(origin, range, () -> hits, resolver, lockTarget);
    }

    // Keep the original lazy behavior when locked and recheck candidates when dynamic acquisition is enabled
    public @Nullable Target<K> update(Vec3 origin, double range, Supplier<? extends Collection<Target<K>>> hits,
                                     Function<K, Target<K>> resolver, boolean lockTarget){
        Target<K> retained = key == null ? null : resolver.apply(key);
        if(lockTarget && inRange(retained, origin, range)) return retained;
        Target<K> next = hits.get().stream().filter(hit -> inRange(hit, origin, range))
                .min(Comparator.<Target<K>>comparingDouble(hit -> distanceSquared(origin, hit.bounds()))
                        .thenComparingInt(hit -> java.util.Objects.equals(key, hit.key()) ? 0 : 1)).orElse(null);
        key = next == null ? null : next.key();
        return next;
    }

    // Drop transient ownership when the observer changes modes or disables tracking
    public void clear(){ key = null; }

    // Read the retained identity without keeping an entity or body alive
    public @Nullable K key(){ return key; }

    // Include large targets whose nearest surface remains within the configured range
    public static double distanceSquared(Vec3 origin, AABB bounds){
        double x = origin.x - Math.clamp(origin.x, bounds.minX, bounds.maxX);
        double y = origin.y - Math.clamp(origin.y, bounds.minY, bounds.maxY);
        double z = origin.z - Math.clamp(origin.z, bounds.minZ, bounds.maxZ);
        return x * x + y * y + z * z;
    }

    private static boolean inRange(@Nullable Target<?> target, Vec3 origin, double range){
        return target != null && Double.isFinite(range) && range > 0
                && distanceSquared(origin, target.bounds()) <= range * range;
    }

    public record Target<K>(K key, AABB bounds){
        public Target{ java.util.Objects.requireNonNull(key); java.util.Objects.requireNonNull(bounds); }
    }
}
