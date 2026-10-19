package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

// Estimate how much of the view a particle trail spans across the screen
public final class ClientParticleScreenCoverage {
    private ClientParticleScreenCoverage() {
    }

    // Measure the trail's angular width relative to the current field of view
    public static double projectedWidth(ClientLevel level, Vec3 start, Vec3 end, double radius) {
        if (level == null || start == null || end == null || radius <= 0.0D) return 0.0D;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.player == null) return 0.0D;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 trail = end.subtract(start);
        double lengthSqr = trail.lengthSqr();
        double along = lengthSqr < 1.0E-8D ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, camera.subtract(start).dot(trail) / lengthSqr));
        double distance = Math.max(0.1D, camera.distanceTo(start.add(trail.scale(along))));
        double fov = Math.max(30.0D, Math.min(110.0D, minecraft.options.fov().get().doubleValue()));
        return Math.atan2(radius, distance) / Math.toRadians(fov * 0.5D);
    }
}
