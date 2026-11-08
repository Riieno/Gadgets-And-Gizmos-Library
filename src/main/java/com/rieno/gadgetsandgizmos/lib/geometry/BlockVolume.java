package com.rieno.gadgetsandgizmos.lib.geometry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Objects;

// Present one continuous block volume through each occupied query cell
public record BlockVolume(BlockPos origin, AABB bounds){
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain immutable local bounds and an immutable anchor position
    public BlockVolume{
        origin = Objects.requireNonNull(origin, "origin").immutable();
        Objects.requireNonNull(bounds, "bounds");
        if(!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
                || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)
                || bounds.getXsize() <= 0 || bounds.getYsize() <= 0 || bounds.getZsize() <= 0){
            throw new IllegalArgumentException("Finite, positive volume dimensions are required");
        }
    }

    // Extend a unit-width column back from a moving block's forward face
    public static BlockVolume column(BlockPos origin, Direction facing, double extension,
                                      double baseFace, double headFace){
        Objects.requireNonNull(facing, "facing");
        if(!Double.isFinite(extension) || extension < 0 || !Double.isFinite(baseFace)
                || !Double.isFinite(headFace) || baseFace < 0 || headFace > 1 || baseFace >= headFace){
            throw new IllegalArgumentException("A finite nonnegative extension and ordered block faces are required");
        }
        double min = baseFace - extension;
        AABB bounds = switch(facing){
            case UP -> new AABB(0, min, 0, 1, headFace, 1);
            case DOWN -> new AABB(0, 1 - headFace, 0, 1, 1 - min, 1);
            case EAST -> new AABB(min, 0, 0, headFace, 1, 1);
            case WEST -> new AABB(1 - headFace, 0, 0, 1 - min, 1, 1);
            case SOUTH -> new AABB(0, 0, min, 1, 1, headFace);
            case NORTH -> new AABB(0, 0, 1 - headFace, 1, 1, 1 - min);
        };
        return new BlockVolume(origin, bounds);
    }

    // Identify query cells which actually overlap the shared volume
    public boolean intersectsCell(BlockPos pos){
        return bounds.intersects(new AABB(pos.subtract(origin)));
    }

    // Return the complete volume in a query cell's local coordinates
    public VoxelShape shapeAt(BlockPos pos){
        if(!intersectsCell(pos)) return Shapes.empty();
        BlockPos offset = origin.subtract(pos);
        return Shapes.create(bounds.move(offset.getX(), offset.getY(), offset.getZ()));
    }

    // Keep a nearer component's ordinary interaction when its surface is hit first
    public boolean hitBefore(Vec3 from, Vec3 to, VoxelShape component){
        var hit = Shapes.create(bounds).clip(from, to, origin);
        if(hit == null) return false;
        var other = component.clip(from, to, origin);
        return other == null || from.distanceToSqr(hit.getLocation()) + 1E-7 < from.distanceToSqr(other.getLocation());
    }
}
