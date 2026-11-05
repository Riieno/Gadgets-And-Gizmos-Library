package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// Report forces transmitted by an attachment but applied to another physical body
public interface ForceTelemetryProvider{
    // Get forces in the owning block entity's body frame, without applying them again
    List<Force> forceTelemetry();

    // Keep the application point and signed force in one local frame
    record Force(ForceGroup group, Vec3 point, Vec3 force){}
}
