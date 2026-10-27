package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.world.phys.Vec3;

// Smooth changing projection anchors at the same speed across different frame rates
public final class ProjectionSmoothing{
    private Vec3 value;
    private double lastTime;

    public Vec3 update(Vec3 target, double seconds){
        if(value == null || seconds < lastTime || seconds - lastTime > 1) value = target;
        else value = value.lerp(target, 1 - Math.exp(-8 * Math.max(0, seconds - lastTime)));
        lastTime = seconds;
        return value;
    }
}
