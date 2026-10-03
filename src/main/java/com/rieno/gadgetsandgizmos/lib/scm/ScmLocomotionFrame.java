package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;

// Convert gait vectors between a gravity-aligned frame and a physical body frame
public record ScmLocomotionFrame(Vec3 right, Vec3 up, Vec3 forward) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Build a horizontal walking frame from world-up expressed in the body frame
    public static ScmLocomotionFrame fromUpAndForward(Vec3 requestedUp, Vec3 requestedForward){
        Vec3 up = normalize(requestedUp, new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 forward = reject(requestedForward, up);
        if(forward.lengthSqr() <= 1.0E-12D){
            forward = Math.abs(up.y) < 0.9D ? up.cross(new Vec3(0.0D, 1.0D, 0.0D))
                    : up.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        forward = normalize(forward, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 right = normalize(up.cross(forward), new Vec3(1.0D, 0.0D, 0.0D));
        return new ScmLocomotionFrame(right, up, normalize(right.cross(up), forward));
    }

    // Convert a physical-body vector into right, up and forward gait coordinates
    public Vec3 toLocal(Vec3 vector){
        Vec3 val = finite(vector);
        return new Vec3(val.dot(right), val.dot(up), val.dot(forward));
    }

    // Convert a gait vector into physical-body coordinates
    public Vec3 toBody(Vec3 vector){
        Vec3 val = finite(vector);
        return right.scale(val.x).add(up.scale(val.y)).add(forward.scale(val.z));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Reject the component parallel to the supplied normal
    private static Vec3 reject(Vec3 vector, Vec3 normal){
        Vec3 val = finite(vector);
        return val.subtract(normal.scale(val.dot(normal)));
    }

    // Normalize a finite vector with a known safe fallback
    private static Vec3 normalize(Vec3 vector, Vec3 fallback){
        Vec3 val = finite(vector);
        return val.lengthSqr() <= 1.0E-12D ? fallback : val.normalize();
    }

    // Replace invalid vectors with zero
    private static Vec3 finite(Vec3 vector){
        return vector == null || !Double.isFinite(vector.x)
                || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)
                ? Vec3.ZERO : vector;
    }
}
