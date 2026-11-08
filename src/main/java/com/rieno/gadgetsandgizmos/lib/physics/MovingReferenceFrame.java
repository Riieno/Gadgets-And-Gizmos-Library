package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

// Describe a rigid frame in world space, with velocity measured at its origin
public record MovingReferenceFrame(Vec3 origin, Quaterniond orientation, Vec3 velocity, Vec3 angularVelocity){
    public MovingReferenceFrame{
        if(!valid(origin) || !valid(velocity) || !valid(angularVelocity) || orientation == null
                || !Double.isFinite(orientation.lengthSquared()) || orientation.lengthSquared() < 1.0E-12D)
            throw new IllegalArgumentException("A moving frame needs a finite pose and velocity");
        orientation = new Quaterniond(orientation).normalize();
    }

    @Override public Quaterniond orientation(){ return new Quaterniond(orientation); }

    // Transform positions without exposing the mutable orientation
    public Vec3 toWorldPosition(Vec3 local){ return origin.add(toWorldDirection(local)); }
    public Vec3 toLocalPosition(Vec3 world){ return toLocalDirection(world.subtract(origin)); }
    public Vec3 toWorldDirection(Vec3 local){
        return vector(orientation.transform(new Vector3d(local.x, local.y, local.z)));
    }
    public Vec3 toLocalDirection(Vec3 world){
        return vector(orientation.transformInverse(new Vector3d(world.x, world.y, world.z)));
    }

    // Include the tangential velocity of points away from the rotation centre
    public Vec3 velocityAt(Vec3 world){ return velocity.add(angularVelocity.cross(world.subtract(origin))); }
    public Vec3 relativeVelocity(Vec3 world, Vec3 actual){ return actual.subtract(velocityAt(world)); }

    // Maintain motion through a rotating frame, including centripetal and Coriolis acceleration
    public Vec3 transportAcceleration(Vec3 world, Vec3 relativeVelocity){
        return angularVelocity.cross(angularVelocity.cross(world.subtract(origin)))
                .add(angularVelocity.cross(relativeVelocity).scale(2.0D));
    }

    // Carry an existing world point or direction with its original rigid frame
    public Vec3 movePositionFrom(MovingReferenceFrame prev, Vec3 world){
        return toWorldPosition(prev.toLocalPosition(world));
    }
    public Vec3 moveDirectionFrom(MovingReferenceFrame prev, Vec3 world){
        return toWorldDirection(prev.toLocalDirection(world));
    }

    public boolean samePose(MovingReferenceFrame other){
        return other != null && origin.distanceToSqr(other.origin) < 1.0E-16D
                && Math.abs(orientation.dot(other.orientation)) > 1.0D - 1.0E-14D;
    }

    private static Vec3 vector(Vector3d val){ return new Vec3(val.x, val.y, val.z); }
    private static boolean valid(Vec3 val){
        return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z);
    }
}
