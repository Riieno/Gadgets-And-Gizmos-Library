package com.rieno.gadgetsandgizmos.lib.physics.archive;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

// Apply one placement rotation and translation to archived world-space geometry
public final class SubLevelArchiveTransform{
    private SubLevelArchiveTransform(){}

    // Move a world point from the archived anchor to the placement anchor
    public static Vec3 point(Vec3 original, Vec3 archivedAnchor, Vec3 target, Quaterniondc rotation){
        Vector3d relative = new Vector3d(original.x - archivedAnchor.x,
                original.y - archivedAnchor.y, original.z - archivedAnchor.z);
        rotation.transform(relative);
        return new Vec3(target.x + relative.x, target.y + relative.y, target.z + relative.z);
    }

    // Conservatively bound all eight rotated corners before validating the destination
    public static AABB bounds(AABB original, Vec3 archivedAnchor, Vec3 target, Quaterniondc rotation){
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for(int x = 0; x < 2; x++) for(int y = 0; y < 2; y++) for(int z = 0; z < 2; z++){
            Vec3 point = point(new Vec3(x == 0 ? original.minX : original.maxX,
                    y == 0 ? original.minY : original.maxY,
                    z == 0 ? original.minZ : original.maxZ), archivedAnchor, target, rotation);
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
