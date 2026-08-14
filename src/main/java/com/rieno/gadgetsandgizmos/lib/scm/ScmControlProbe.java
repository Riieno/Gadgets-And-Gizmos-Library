package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;

// Define a control which the SCM can safely probe, restore and command
public interface ScmControlProbe {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the adapter id
    String adapterId();

    // Get the SCM control probe display name
    String displayName();

    // Group probes which share one physical control so only the strongest value is applied
    default String controlGroupId() {
        return "";
    }

    // Get the minimum control
    double minControl();

    // Get the maximum control
    double maxControl();

    // Get the neutral control
    default double neutralControl() {
        return 0.0D;
    }

    // Apply the SCM control probe
    void apply(double control);

    // Read the SCM control probe
    Reading read();

    // Get the target-sublevel local effect direction
    Vec3 localEffectDirection();

    // Get the target-sublevel local effect position
    Vec3 localEffectPosition();

    // Check if this is available
    default boolean isAvailable() {
        return true;
    }

    // Restore the SCM control probe
    void restore();

    // Store the reading
    record Reading(double speed, double effect, boolean active) {
        // Initialize the reading
        public Reading {
            speed = Double.isFinite(speed) ? speed : 0.0D;
            effect = Double.isFinite(effect) ? effect : 0.0D;
        }
    }
}
