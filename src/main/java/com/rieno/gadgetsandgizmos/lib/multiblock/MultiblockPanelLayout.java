package com.rieno.gadgetsandgizmos.lib.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

// Place decorative panels on multiblock surfaces without creating blocks or collision shapes
public final class MultiblockPanelLayout{
    private static final Map<Layout, List<Panel>> CACHE = new LinkedHashMap<>();
    private MultiblockPanelLayout(){}

    public static List<Panel> panels(BlockPos origin, int sizeX, int sizeY, int sizeZ,
                                     Direction.Axis axis, boolean allSides, boolean lowMounted){
        int x = Math.max(1, sizeX), y = Math.max(1, sizeY), z = Math.max(1, sizeZ);
        var key = new Layout(origin.immutable(), x, y, z, axis, allSides, lowMounted);
        synchronized(CACHE){
            var prev = CACHE.get(key);
            if(prev != null) return prev;
        }
        double height = lowMounted ? Math.min(y - .5D, 1.5D) : y / 2.0D;
        List<Panel> panels = new ArrayList<>();
        for(Direction face : Direction.Plane.HORIZONTAL){
            if(!allSides && face.getAxis() != axis) continue;
            double px = face == Direction.WEST ? 0 : face == Direction.EAST ? x : x / 2.0D;
            double pz = face == Direction.NORTH ? 0 : face == Direction.SOUTH ? z : z / 2.0D;
            Vec3 center = new Vec3(origin.getX() + px, origin.getY() + height, origin.getZ() + pz);
            BlockPos owner = BlockPos.containing(center.add(-face.getStepX() * .01D, 0, -face.getStepZ() * .01D));
            panels.add(new Panel(owner, center, face));
        }
        var res = List.copyOf(panels);
        synchronized(CACHE){
            var prev = CACHE.get(key);
            if(prev != null) return prev;
            if(CACHE.size() >= 2048) CACHE.remove(CACHE.keySet().iterator().next());
            CACHE.put(key, res);
        }
        return res;
    }

    private record Layout(BlockPos origin, int x, int y, int z, Direction.Axis axis, boolean allSides, boolean lowMounted){}

    public record Panel(BlockPos owner, Vec3 center, Direction facing){
        // Include the housing thickness so its front, sides and edges share the same interaction
        public net.minecraft.world.phys.AABB bounds(double width, double height, double depth){
            double halfX = facing.getAxis() == Direction.Axis.Z ? width / 2 : depth / 2;
            double halfZ = facing.getAxis() == Direction.Axis.X ? width / 2 : depth / 2;
            Vec3 middle = center.add(facing.getStepX() * depth / 2, 0, facing.getStepZ() * depth / 2);
            return new net.minecraft.world.phys.AABB(middle.x - halfX, middle.y - height / 2, middle.z - halfZ,
                    middle.x + halfX, middle.y + height / 2, middle.z + halfZ);
        }

        // Project a world-space hit into an upright panel of the supplied width and height
        public boolean contains(Vec3 hit, Direction face, double width, double height){
            if(face != facing) return false;
            Vec3 offset = hit.subtract(center);
            double horizontal = offset.x * facing.getStepZ() - offset.z * facing.getStepX();
            return Math.abs(horizontal) <= width / 2 && Math.abs(offset.y) <= height / 2;
        }
    }
}
