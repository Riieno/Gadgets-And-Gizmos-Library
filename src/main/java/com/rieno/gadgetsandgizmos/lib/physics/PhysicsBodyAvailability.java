package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;

// Query native body membership without waking a body or loading its chunks
public interface PhysicsBodyAvailability{
    // Check whether this exact body is registered in the current native scene
    boolean isBodyLoaded(PhysicsPipelineBody body);
}
