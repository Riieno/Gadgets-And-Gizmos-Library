package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

// Sample every assembly body's current plot bounds and articulation
public final class SableAssemblyHullApi{
    private SableAssemblyHullApi(){}

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

    // Read plot geometry directly so cached Sable world envelopes cannot hide a new carriage
    public static Snapshot sample(SubLevel root, Collection<? extends SubLevel> bodies){
        List<AABB> world = new ArrayList<>();
        Map<UUID, AABB> shape = new LinkedHashMap<>();
        if(root == null || bodies == null) return new Snapshot(world, shape);
        for(SubLevel body : bodies){
            if(body == null || body.isRemoved() || body.getUniqueId() != null && shape.containsKey(body.getUniqueId())) continue;
            var plot = body.getPlot();
            var local = plot == null ? null : plot.getBoundingBox();
            AABB bounds;
            AABB relative;
            if(local != null){
                AABB blocks = new AABB(local.minX(), local.minY(), local.minZ(),
                        local.maxX() + 1.0D, local.maxY() + 1.0D, local.maxZ() + 1.0D);
                bounds = transform(blocks, body.logicalPose()::transformPosition);
                relative = transform(blocks, point -> root.logicalPose().transformPositionInverse(
                        body.logicalPose().transformPosition(point)));
            }else{
                var envelope = body.boundingBox();
                if(envelope == null) continue;
                bounds = new AABB(envelope.minX(), envelope.minY(), envelope.minZ(),
                        envelope.maxX(), envelope.maxY(), envelope.maxZ());
                relative = transform(bounds, root.logicalPose()::transformPositionInverse);
            }
            world.add(bounds);
            if(body.getUniqueId() != null) shape.put(body.getUniqueId(), relative);
        }
        return new Snapshot(world, shape);
    }

    // Bound all transformed corners rather than only the lead body's centre
    private static AABB transform(AABB bounds, UnaryOperator<Vec3> transform){
        AABB res = null;
        for(double x : new double[]{bounds.minX, bounds.maxX}){
            for(double y : new double[]{bounds.minY, bounds.maxY}){
                for(double z : new double[]{bounds.minZ, bounds.maxZ}){
                    Vec3 point = transform.apply(new Vec3(x, y, z));
                    AABB corner = new AABB(point, point);
                    res = res == null ? corner : res.minmax(corner);
                }
            }
        }
        return res;
    }
}
