package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.world.phys.Vec3;

// Describe a propeller's physical point and unsigned axis in its owner's local frame
public interface PropellerForceGeometry{
    // Get the current thrust application point
    Vec3 propellerForcePosition();

    // Get the current thrust axis before applying the propeller's signed thrust
    Vec3 propellerForceDirection();

    // Report full-speed capacity without changing the shaft speed; NaN retains legacy discovery.
    default double maximumPropellerThrust(){ return Double.NaN; }
}
