package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

import java.util.List;

// Resolve the turning reference from host-supplied actuator positions
public final class ScmSteeringGeometry{
    private ScmSteeringGeometry(){}

    // Use the steering axle rather than the hull centre to preview corners
    public static Vec3 referencePosition(Vec3 center, Vec3 forward, List<Vec3> actuators,
                                         ScmSteeringMode mode, boolean reverse){
        if(center == null || forward == null || actuators == null || actuators.isEmpty()) return center;
        Vec3 axis = new Vec3(forward.x, 0.0D, forward.z).normalize();
        if(axis.lengthSqr() <= 1.0E-12D) return center;
        double trailing = Double.POSITIVE_INFINITY;
        double leading = Double.NEGATIVE_INFINITY;
        for(Vec3 pos : actuators){
            if(!finite(pos)) continue;
            double distance = pos.subtract(center).dot(axis);
            trailing = Math.min(trailing, distance);
            leading = Math.max(leading, distance);
        }
        if(!Double.isFinite(leading)) return center;
        boolean rear = mode == ScmSteeringMode.REAR_WHEEL
                || reverse && mode != ScmSteeringMode.FRONT_WHEEL;
        double midpoint = (trailing + leading) * 0.5D;
        double total = 0.0D;
        int count = 0;
        for(Vec3 pos : actuators){
            if(!finite(pos)) continue;
            double distance = pos.subtract(center).dot(axis);
            if(leading - trailing > 0.25D && (rear ? distance > midpoint : distance < midpoint)) continue;
            total += distance;
            count++;
        }
        return count == 0 ? center : center.add(axis.scale(total / count));
    }

    // Ignore unavailable actuator coordinates
    private static boolean finite(Vec3 val){
        return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z);
    }
}
