package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.UnaryOperator;

// Sample every assembly body's current plot bounds and articulation
public final class SableAssemblyHullApi{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ThreadLocal<Map<SubLevel, CachedBounds>> WORLD_BOUNDS = ThreadLocal.withInitial(WeakHashMap::new);

    private SableAssemblyHullApi(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Reuse an immutable envelope until the actual pose or occupied plot bounds change
    public static @Nullable AABB worldBounds(SubLevel body){
        if(body == null || body.isRemoved()) return null;
        var plot = body.getPlot();
        var local = plot == null ? null : plot.getBoundingBox();
        var fallback = local == null ? body.boundingBox() : null;
        if(local == null && fallback == null) return null;
        double minX = local == null ? fallback.minX() : local.minX();
        double minY = local == null ? fallback.minY() : local.minY();
        double minZ = local == null ? fallback.minZ() : local.minZ();
        double maxX = local == null ? fallback.maxX() : local.maxX();
        double maxY = local == null ? fallback.maxY() : local.maxY();
        double maxZ = local == null ? fallback.maxZ() : local.maxZ();
        Pose3dc pose = body.logicalPose();
        Map<SubLevel, CachedBounds> cache = WORLD_BOUNDS.get();
        CachedBounds prev = cache.get(body);
        if(prev != null && prev.matches(local != null, minX, minY, minZ, maxX, maxY, maxZ, pose)) return prev.bounds();
        AABB bounds = null;
        if(minX <= maxX && minY <= maxY && minZ <= maxZ){
            bounds = local == null ? new AABB(minX, minY, minZ, maxX, maxY, maxZ)
                    : new BoundingBox3d(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D)
                    .transform(pose).toMojang();
        }
        cache.put(body, new CachedBounds(local != null, minX, minY, minZ, maxX, maxY, maxZ,
                new Pose3d().set(pose), bounds));
        return bounds;
    }

    // Keep world clearance and root-frame shape in the same telemetry sample
    public record Snapshot(List<AABB> worldBounds, Map<UUID, AABB> rootBounds){
        public Snapshot{
            worldBounds = List.copyOf(worldBounds);
            rootBounds = Map.copyOf(rootBounds);
        }

        // Detect coupling, block edits and sway without reacting to shared translation or rotation
        public boolean changedFrom(Snapshot prev, double tolerance){
            if(prev == null || !rootBounds.keySet().equals(prev.rootBounds.keySet())) return true;
            double limit = Math.max(0.0D, tolerance);
            for(var entry : rootBounds.entrySet()){
                AABB next = entry.getValue();
                AABB old = prev.rootBounds.get(entry.getKey());
                if(Math.abs(next.minX - old.minX) > limit || Math.abs(next.maxX - old.maxX) > limit
                        || Math.abs(next.minY - old.minY) > limit || Math.abs(next.maxY - old.maxY) > limit
                        || Math.abs(next.minZ - old.minZ) > limit || Math.abs(next.maxZ - old.maxZ) > limit) return true;
            }
            return false;
        }
    }

    // Distinguish actual construction edits from normal articulation during navigation
    public static Map<UUID, AABB> localBounds(Collection<? extends SubLevel> bodies){
        Map<UUID, AABB> res = new LinkedHashMap<>();
        for(SubLevel body : bodies){
            if(body == null || body.isRemoved() || body.getPlot() == null) continue;
            var bounds = body.getPlot().getBoundingBox();
            if(bounds == null || bounds.minX() > bounds.maxX() || bounds.minY() > bounds.maxY() || bounds.minZ() > bounds.maxZ()) continue;
            res.put(body.getUniqueId(), new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX() + 1.0D, bounds.maxY() + 1.0D, bounds.maxZ() + 1.0D));
        }
        return Map.copyOf(res);
    }

    // Read plot geometry directly so cached Sable world envelopes cannot hide a new carriage
    public static Snapshot sample(SubLevel root, Collection<? extends SubLevel> bodies){
        List<AABB> world = new ArrayList<>();
        Map<UUID, AABB> shape = new LinkedHashMap<>();
        if(root == null || bodies == null) return new Snapshot(world, shape);
        for(SubLevel body : bodies){
            if(body == null || body.isRemoved() || body.getUniqueId() != null && shape.containsKey(body.getUniqueId())) continue;
            var plot = body.getPlot();
            var local = plot == null ? null : plot.getBoundingBox();
            AABB bounds = worldBounds(body);
            if(bounds == null) continue;
            AABB relative;
            if(local != null){
                if(local.minX() > local.maxX() || local.minY() > local.maxY() || local.minZ() > local.maxZ()) continue;
                AABB blocks = new AABB(local.minX(), local.minY(), local.minZ(),
                        local.maxX() + 1.0D, local.maxY() + 1.0D, local.maxZ() + 1.0D);
                relative = body == root ? blocks : transform(blocks, point -> root.logicalPose().transformPositionInverse(
                        body.logicalPose().transformPosition(point)));
            }else{
                var envelope = body.boundingBox();
                if(envelope == null) continue;
                relative = transform(bounds, root.logicalPose()::transformPositionInverse);
            }
            world.add(bounds);
            if(body.getUniqueId() != null) shape.put(body.getUniqueId(), relative);
        }
        return new Snapshot(world, shape);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound all transformed corners rather than only the lead body's centre
    private static AABB transform(AABB bounds, UnaryOperator<Vec3> transform){
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for(int idx = 0; idx < 8; idx++){
            Vec3 point = transform.apply(new Vec3((idx & 1) == 0 ? bounds.minX : bounds.maxX,
                    (idx & 2) == 0 ? bounds.minY : bounds.maxY, (idx & 4) == 0 ? bounds.minZ : bounds.maxZ));
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // Compare source values rather than a tick number so teleports and edits are visible immediately
    private record CachedBounds(boolean local, double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ, Pose3d pose, @Nullable AABB bounds){
        private boolean matches(boolean local, double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ, Pose3dc pose){
            return this.local == local && this.minX == minX && this.minY == minY && this.minZ == minZ
                    && this.maxX == maxX && this.maxY == maxY && this.maxZ == maxZ
                    && this.pose.position().equals(pose.position()) && this.pose.orientation().equals(pose.orientation())
                    && this.pose.rotationPoint().equals(pose.rotationPoint()) && this.pose.scale().equals(pose.scale());
        }
    }
}
