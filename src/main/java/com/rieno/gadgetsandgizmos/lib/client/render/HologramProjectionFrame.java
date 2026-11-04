package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

// Map source-world blocks into an interpolated miniature projection
public record HologramProjectionFrame(Vec3 target, Quaternionf orientation, Vec3 scale, Vec3 origin){
    // Express source-world geometry in the reference frame before projecting it
    public HologramProjectionFrame alignSource(Quaternionfc referenceOrientation){
        return new HologramProjectionFrame(target,
                new Quaternionf(orientation).mul(new Quaternionf(referenceOrientation).invert()), scale, origin);
    }

    // Match source heading while retaining the projection's own up axis, pitch and roll
    public HologramProjectionFrame alignSourceHeading(Quaternionfc referenceOrientation){
        return alignSource(heading(referenceOrientation));
    }

    // Keep the destination level with world up while retaining its horizontal heading
    public HologramProjectionFrame lockWorldUp(){
        return new HologramProjectionFrame(target, heading(orientation), scale, origin);
    }

    private static Quaternionf heading(Quaternionfc referenceOrientation){
        Vector3f forward = referenceOrientation.transform(new Vector3f(0, 0, -1));
        double heading;
        if(forward.x * forward.x + forward.z * forward.z > 1.0E-8F){
            heading = Math.atan2(-forward.x, -forward.z);
        }else{
            Vector3f right = referenceOrientation.transform(new Vector3f(1, 0, 0));
            heading = Math.atan2(-right.z, right.x);
        }
        return new Quaternionf().rotationY((float)heading);
    }

    public Matrix4f blockTransform(Vec3i renderOrigin, SubLevelClientRenderApi.WorldFrame source){
        return new Matrix4f().translation((float)(target.x - renderOrigin.getX()),
                (float)(target.y - renderOrigin.getY()), (float)(target.z - renderOrigin.getZ()))
                .rotate(orientation).scale((float)scale.x, (float)scale.y, (float)scale.z)
                .translate((float)(source.position().x - origin.x), (float)(source.position().y - origin.y), (float)(source.position().z - origin.z))
                .rotate(source.orientation()).scale((float)source.scale().x, (float)source.scale().y, (float)source.scale().z)
                .translate(-0.5F, -0.5F, -0.5F);
    }
}
