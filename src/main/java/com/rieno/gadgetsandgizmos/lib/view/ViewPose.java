package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

// Carry a world-space lens position and orientation
public record ViewPose(Vec3 position, Quaterniondc orientation, double fov){
    // Copy the orientation and bound the lens field of view
    public ViewPose{
        orientation = new Quaterniond(orientation).normalize();
        fov = Double.isFinite(fov) ? Math.clamp(fov, 5.0D, 120.0D) : 70.0D;
    }

    // Return a separate orientation so callers cannot change the pose
    @Override
    public Quaterniondc orientation(){
        return new Quaterniond(orientation);
    }

    // Resolve the lens forward direction
    public Vec3 forward(){
        Vector3d dir = orientation.transform(new Vector3d(0, 0, -1));
        return new Vec3(dir.x, dir.y, dir.z);
    }
}
