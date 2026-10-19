package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

// Aim a mounted lens at target bounds and retain space around their projected outline
public final class ViewTargetFraming{
    private ViewTargetFraming(){}

    // Resolve the target bearing in the same local or global frame used by a view rig
    public static ViewRig.Angles aim(Vec3 pivot, Vec3 target, Quaterniondc mount, ViewRig.Orientation frame){
        Vec3 offset = target.subtract(pivot);
        if(offset.lengthSqr() < 1.0E-12) return new ViewRig.Angles(0, 0);
        Vector3d dir = new Vector3d(offset.x, offset.y, offset.z).normalize();
        if(frame == ViewRig.Orientation.LOCAL) mount.transformInverse(dir);
        double pan = Math.toDegrees(Math.atan2(-dir.x, -dir.z));
        double pitch = Math.toDegrees(Math.asin(Math.clamp(dir.y, -1, 1)));
        return new ViewRig.Angles(pan, pitch - 90);
    }

    // Fit every corner in both axes using a vertical field of view and the host's aspect ratio
    public static double fieldOfView(ViewPose pose, AABB bounds, double aspect){
        double ratio = Double.isFinite(aspect) && aspect > 0 ? aspect : 1;
        double spread = 0;
        for(int idx = 0; idx < 8; idx++){
            Vec3 point = new Vec3((idx & 1) == 0 ? bounds.minX : bounds.maxX,
                    (idx & 2) == 0 ? bounds.minY : bounds.maxY, (idx & 4) == 0 ? bounds.minZ : bounds.maxZ);
            Vec3 offset = point.subtract(pose.position());
            Vector3d local = pose.orientation().transformInverse(new Vector3d(offset.x, offset.y, offset.z));
            if(local.z >= -1.0E-6) return 120;
            spread = Math.max(spread, Math.max(Math.abs(local.y), Math.abs(local.x) / ratio) / -local.z);
        }
        return Math.clamp(Math.toDegrees(2 * Math.atan(spread * 1.25)), 5, 120);
    }

    // Widen a live pose when a narrower host would otherwise clip the target
    public static ViewPose fit(ViewPose pose, AABB bounds, double aspect){
        return new ViewPose(pose.position(), pose.orientation(), Math.max(pose.fov(), fieldOfView(pose, bounds, aspect)));
    }

    // Synchronize transient target geometry without retaining its owning entity or body
    public static CompoundTag boundsTag(AABB bounds){
        CompoundTag tag = new CompoundTag();
        tag.putDouble("MinX", bounds.minX);
        tag.putDouble("MinY", bounds.minY);
        tag.putDouble("MinZ", bounds.minZ);
        tag.putDouble("MaxX", bounds.maxX);
        tag.putDouble("MaxY", bounds.maxY);
        tag.putDouble("MaxZ", bounds.maxZ);
        return tag;
    }

    // Reject missing or invalid network geometry before calculating a projection
    public static @Nullable AABB boundsFromTag(CompoundTag tag){
        for(String key : new String[]{"MinX", "MinY", "MinZ", "MaxX", "MaxY", "MaxZ"}){
            if(!tag.contains(key) || !Double.isFinite(tag.getDouble(key))) return null;
        }
        return new AABB(tag.getDouble("MinX"), tag.getDouble("MinY"), tag.getDouble("MinZ"),
                tag.getDouble("MaxX"), tag.getDouble("MaxY"), tag.getDouble("MaxZ"));
    }
}
