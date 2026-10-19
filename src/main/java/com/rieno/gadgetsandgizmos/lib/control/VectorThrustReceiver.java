package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.world.phys.Vec3;

// Accept a local force while retaining the engine's native physics and fuel logic
public interface VectorThrustReceiver{
    // Report the live steering cone around the provider's nominal thrust direction
    default double controllerThrustConeDegrees(){ return 0.0D; }

    // Claim the channel and apply a force, including an owned zero force
    void applyVectorControllerForce(String channelId, Vec3 force);

    // Release only the named owner's force
    void releaseVectorControllerForce(String channelId);
}
