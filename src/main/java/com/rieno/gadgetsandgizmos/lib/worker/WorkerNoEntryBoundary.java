package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Predicate;

// Treat each no-entry area as a wall that extends upward from its lower face
public final class WorkerNoEntryBoundary{
    private static final double CLEARANCE = 0.03125D;

    private WorkerNoEntryBoundary(){}

    public static boolean blocks(BlockPos pos, Collection<WorkerArea> areas){
        if(pos == null || areas == null) return false;
        return areas.stream().anyMatch(area -> pos.getY() >= area.min().getY()
                && pos.getX() >= area.min().getX() && pos.getX() <= area.max().getX()
                && pos.getZ() >= area.min().getZ() && pos.getZ() <= area.max().getZ());
    }

    public static boolean intersects(AABB bounds, Collection<WorkerArea> areas){
        if(bounds == null || areas == null) return false;
        return areas.stream().anyMatch(area -> bounds.maxY > area.min().getY()
                && bounds.maxX > area.min().getX() && bounds.minX < area.max().getX() + 1.0D
                && bounds.maxZ > area.min().getZ() && bounds.minZ < area.max().getZ() + 1.0D);
    }

    // Return the first point along a movement where the whole body reaches a wall
    public static double firstBlockedFraction(AABB bounds, Vec3 movement, Collection<WorkerArea> areas){
        if(bounds == null || movement == null || areas == null) return 1.0D;
        double first = 1.0D;
        for(WorkerArea area : areas){
            double minX = area.min().getX();
            double maxX = area.max().getX() + 1.0D;
            double minZ = area.min().getZ();
            double maxZ = area.max().getZ() + 1.0D;
            double enter = 0.0D;
            double exit = first;
            if(movement.x > 0.0D){
                enter = Math.max(enter, (minX - bounds.maxX) / movement.x);
                exit = Math.min(exit, (maxX - bounds.minX) / movement.x);
            } else if(movement.x < 0.0D){
                enter = Math.max(enter, (maxX - bounds.minX) / movement.x);
                exit = Math.min(exit, (minX - bounds.maxX) / movement.x);
            } else if(bounds.maxX <= minX || bounds.minX >= maxX) continue;
            if(movement.z > 0.0D){
                enter = Math.max(enter, (minZ - bounds.maxZ) / movement.z);
                exit = Math.min(exit, (maxZ - bounds.minZ) / movement.z);
            } else if(movement.z < 0.0D){
                enter = Math.max(enter, (maxZ - bounds.minZ) / movement.z);
                exit = Math.min(exit, (minZ - bounds.maxZ) / movement.z);
            } else if(bounds.maxZ <= minZ || bounds.minZ >= maxZ) continue;
            if(movement.y > 0.0D)
                enter = Math.max(enter, (area.min().getY() - bounds.maxY) / movement.y);
            else if(movement.y < 0.0D)
                exit = Math.min(exit, (area.min().getY() - bounds.maxY) / movement.y);
            else if(bounds.maxY <= area.min().getY()) continue;
            if(enter < exit) first = Math.min(first, enter);
        }
        return first;
    }

    // Find the nearest horizontal exit that clears the worker's whole body
    public static @Nullable Vec3 nearestExit(Vec3 position, AABB bounds, Collection<WorkerArea> areas,
                                              Predicate<Vec3> usable){
        if(position == null || bounds == null || areas == null || areas.isEmpty()) return null;
        Predicate<Vec3> valid = usable == null ? candidate -> true : usable;
        double westWidth = position.x - bounds.minX;
        double eastWidth = bounds.maxX - position.x;
        double northWidth = position.z - bounds.minZ;
        double southWidth = bounds.maxZ - position.z;
        List<Vec3> candidates = new ArrayList<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for(WorkerArea area : areas){
            minX = Math.min(minX, area.min().getX());
            maxX = Math.max(maxX, area.max().getX());
            minZ = Math.min(minZ, area.min().getZ());
            maxZ = Math.max(maxZ, area.max().getZ());
            if(!intersects(bounds, List.of(area))) continue;
            candidates.add(new Vec3(area.min().getX() - eastWidth - CLEARANCE, position.y, position.z));
            candidates.add(new Vec3(area.max().getX() + 1.0D + westWidth + CLEARANCE, position.y, position.z));
            candidates.add(new Vec3(position.x, position.y, area.min().getZ() - southWidth - CLEARANCE));
            candidates.add(new Vec3(position.x, position.y, area.max().getZ() + 1.0D + northWidth + CLEARANCE));
        }
        candidates.add(new Vec3(minX - eastWidth - CLEARANCE, position.y, position.z));
        candidates.add(new Vec3(maxX + 1.0D + westWidth + CLEARANCE, position.y, position.z));
        candidates.add(new Vec3(position.x, position.y, minZ - southWidth - CLEARANCE));
        candidates.add(new Vec3(position.x, position.y, maxZ + 1.0D + northWidth + CLEARANCE));
        return candidates.stream().filter(candidate -> !intersects(bounds.move(candidate.subtract(position)), areas))
                .filter(valid).min(Comparator.comparingDouble(position::distanceToSqr)).orElse(null);
    }
}
