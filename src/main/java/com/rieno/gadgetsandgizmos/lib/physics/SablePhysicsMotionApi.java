package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Optional;

// Sample the selected loaded body's world motion without loading chunks or walking to another body
public final class SablePhysicsMotionApi{
    private SablePhysicsMotionApi(){}

    public static Optional<MovingReferenceFrame> sample(@Nullable ServerSubLevel body){
        if(!SablePhysicsBodyApi.isLoaded(body)) return Optional.empty();
        try{
            var handle = RigidBodyHandle.of(body);
            if(handle == null || !handle.isValid() || body.getMassTracker() == null) return Optional.empty();
            var pose = body.logicalPose();
            Vector3d center = pose.transformPosition(body.getMassTracker().getCenterOfMass(), new Vector3d());
            var anchor = body.getPlot().getCenterBlock();
            Vector3d origin = pose.transformPosition(new Vector3d(anchor.getX(), anchor.getY(), anchor.getZ()), new Vector3d());
            Vec3 angular = vector(handle.getAngularVelocity(new Vector3d()));
            Vec3 velocity = vector(handle.getLinearVelocity(new Vector3d()))
                    .add(angular.cross(vector(origin).subtract(vector(center))));
            return Optional.of(new MovingReferenceFrame(vector(origin), new org.joml.Quaterniond(pose.orientation()), velocity, angular));
        }catch(RuntimeException | LinkageError err){
            return Optional.empty();
        }
    }

    private static Vec3 vector(Vector3d val){ return new Vec3(val.x, val.y, val.z); }
}
