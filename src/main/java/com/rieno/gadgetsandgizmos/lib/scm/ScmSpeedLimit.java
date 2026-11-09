package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

// Apply a persistent physical speed ceiling independently of a vehicle's propulsion and navigation mode
public record ScmSpeedLimit(double blocksPerSecond){
    public static final ScmSpeedLimit UNLIMITED = new ScmSpeedLimit(0);

    // Treat nonpositive or unavailable values as an unrestricted controller
    public ScmSpeedLimit{
        blocksPerSecond = Double.isFinite(blocksPerSecond) ? Math.max(0, blocksPerSecond) : 0;
    }

    public boolean enabled(){ return blocksPerSecond > 0; }

    // Keep explicit physical goals within the configured ceiling
    public double cap(double speed){
        double requested = Double.isFinite(speed) ? Math.max(0, speed) : 0;
        return enabled() ? Math.min(requested, blocksPerSecond) : requested;
    }

    // Use the configured physical maximum as the percentage reference when one is set
    public double percentage(double fraction, double unrestrictedSpeed){
        double requested = Double.isFinite(fraction) ? Math.clamp(fraction, 0, 1) : 0;
        double reference = enabled() ? blocksPerSecond : Double.isFinite(unrestrictedSpeed) ? Math.max(0, unrestrictedSpeed) : 0;
        return requested * reference;
    }

    // Bound predicted drive velocity while allowing existing braking and gradual overspeed recovery
    public Vec3 limitAcceleration(Vec3 velocity, Vec3 acceleration, double tickSeconds, double responseSeconds){
        if(!enabled()) return acceleration;
        double dt = Double.isFinite(tickSeconds) ? Math.max(1.0E-5D, tickSeconds) : 0.05D;
        double response = Double.isFinite(responseSeconds) ? Math.max(dt, responseSeconds) : dt;
        double speed = velocity.length();
        double allowed = speed > blocksPerSecond ? speed - (speed - blocksPerSecond) * Math.min(1, dt / response) : blocksPerSecond;
        Vec3 next = velocity.add(acceleration.scale(dt));
        if(next.lengthSqr() <= allowed * allowed) return acceleration;
        return next.normalize().scale(allowed).subtract(velocity).scale(1 / dt);
    }
}
