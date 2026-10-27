package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

// Map source-world blocks into an interpolated miniature projection
public record HologramProjectionFrame(Vec3 target, Quaternionf orientation, Vec3 scale, Vec3 origin){
    public Matrix4f blockTransform(Vec3i renderOrigin, SubLevelClientRenderApi.WorldFrame source){
        return new Matrix4f().translation((float)(target.x - renderOrigin.getX()),
                (float)(target.y - renderOrigin.getY()), (float)(target.z - renderOrigin.getZ()))
                .rotate(orientation).scale((float)scale.x, (float)scale.y, (float)scale.z)
                .translate((float)(source.position().x - origin.x), (float)(source.position().y - origin.y), (float)(source.position().z - origin.z))
                .rotate(source.orientation()).scale((float)source.scale().x, (float)source.scale().y, (float)source.scale().z)
                .translate(-0.5F, -0.5F, -0.5F);
    }
}
