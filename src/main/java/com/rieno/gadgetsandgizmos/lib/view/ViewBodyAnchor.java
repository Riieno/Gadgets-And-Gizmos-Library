package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// Retain a body's position in the frame it occupied before remote control
public final class ViewBodyAnchor{
    private final SubLevel body;
    private final Vec3 localPos;
    private Vec3 lastPos;

    // Capture the tracked frame independently of the viewed camera's frame
    public ViewBodyAnchor(Entity entity){
        body = SableLevelApi.tracking(entity);
        lastPos = entity.position();
        localPos = SableTransformApi.toLocalPosition(body, lastPos);
    }

    // Follow the retained frame and keep the last world position if it unloads
    public Vec3 position(){
        if(body == null) return localPos;
        if(!body.isRemoved()) lastPos = SableTransformApi.toWorldPosition(body, localPos);
        return lastPos;
    }
}
