package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;

// Share Physics Staff-style distance and rotation controls with world previews
public final class WorldPlacementControls{
    private final Quaterniond orientation = new Quaterniond();
    private final double maximumDistance;
    private double distance;

    public WorldPlacementControls(double initialDistance, double maximumDistance){
        this.maximumDistance = Math.max(2.0D, maximumDistance);
        distance = Math.clamp(initialDistance, 2.0D, this.maximumDistance);
    }

    // Move the placement point along the player's look direction
    public Vec3 target(Vec3 eye, Vec3 look){
        return eye.add(look.scale(distance));
    }

    // Match the Physics Staff's distance scaling and sprint multiplier
    public void scroll(double delta, double sensitivity, boolean sprint){
        double speed = Math.clamp(Math.sqrt(distance / 10.0D), 1.0D, 5.0D)
                * (sprint ? 4.0D : 1.0D);
        distance = Math.clamp(distance + delta * sensitivity * speed, 2.0D, maximumDistance);
    }

    // Rotate around local up and the player's horizontal right axis
    public void rotate(double yaw, double pitch, double sensitivity, Vec3 right){
        orientation.rotateLocalY(Math.toRadians(yaw) * sensitivity);
        orientation.premul(new Quaterniond(new AxisAngle4d(-Math.toRadians(pitch) * sensitivity,
                right.x, right.y, right.z))).normalize();
    }

    public Quaterniond orientation(){ return new Quaterniond(orientation); }
    public double distance(){ return distance; }
}
