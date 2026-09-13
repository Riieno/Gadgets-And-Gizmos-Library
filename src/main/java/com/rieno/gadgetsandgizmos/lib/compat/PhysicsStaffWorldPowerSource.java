package com.rieno.gadgetsandgizmos.lib.compat;

import java.util.UUID;

// Supply Physics Staff lock power from a loaded world object
public interface PhysicsStaffWorldPowerSource {
    enum Status {
        AVAILABLE,
        STARTING,
        UNAVAILABLE
    }

    // Get the staff supplied by this source
    UUID physicsStaffId();

    // Get the current source status
    Status physicsStaffPowerStatus();

    // Consume the requested whole-air amount
    boolean consumePhysicsStaffPower(int amount);
}
