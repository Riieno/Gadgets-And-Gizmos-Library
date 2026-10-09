package com.rieno.gadgetsandgizmos.lib.geometry;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

// Rotate model boxes into Minecraft's axis-aligned collision representation
public final class VoxelShapeTransforms{
    private VoxelShapeTransforms(){}

    // Rotate each box about a pivot and retain its enclosing axis-aligned bounds
    public static VoxelShape rotate(VoxelShape shape, Quaterniondc rotation, Vec3 pivot){
        VoxelShape res = Shapes.empty();
        Vector3d corner = new Vector3d();
        for(var box : shape.toAabbs()){
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for(int idx = 0; idx < 8; idx++){
                corner.set((idx & 1) == 0 ? box.minX : box.maxX,
                        (idx & 2) == 0 ? box.minY : box.maxY,
                        (idx & 4) == 0 ? box.minZ : box.maxZ).sub(pivot.x, pivot.y, pivot.z);
                rotation.transform(corner);
                corner.add(pivot.x, pivot.y, pivot.z);
                minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y); minZ = Math.min(minZ, corner.z);
                maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y); maxZ = Math.max(maxZ, corner.z);
            }
            res = Shapes.or(res, Shapes.box(snap(minX), snap(minY), snap(minZ), snap(maxX), snap(maxY), snap(maxZ)));
        }
        return res;
    }

    // Keep cardinal rotations on exact model coordinates despite floating point noise
    private static double snap(double val){
        double grid = Math.rint(val * 16) / 16;
        return Math.abs(val - grid) < 1E-10 ? grid : val;
    }
}
