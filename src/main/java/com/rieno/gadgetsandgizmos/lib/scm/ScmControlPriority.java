package com.rieno.gadgetsandgizmos.lib.scm;

import java.util.Set;
import net.minecraft.world.phys.Vec3;

/** Keeps direct rotation responsive while altitude hold requests lift. */
public final class ScmControlPriority {
    private static final Set<String> DIRECT_ROTATION = Set.of(
            "ship_yaw", "ship_pan", "ship_yaw_left", "ship_yaw_right",
            "ship_pitch", "ship_tilt", "ship_pitch_up", "ship_pitch_down",
            "ship_roll", "ship_roll_left", "ship_roll_right");

    private ScmControlPriority() {
    }

    public static boolean isDirectRotation(String action) {
        return DIRECT_ROTATION.contains(action);
    }

    // Retain optional control requests only on axes that the primary SCM commands leave free
    public static Vec3 remainingForce(Vec3 request, Set<String> primary, Vec3 forward, Vec3 up){
        Vec3 res = request;
        if(primary.stream().anyMatch(val -> Set.of("ship_forward", "ship_backward", "ship_reverse", "ship_accelerate").contains(val))){
            res = removeAxis(res, forward);
        }
        if(primary.stream().anyMatch(val -> val.startsWith("ship_strafe"))) res = removeAxis(res, forward.cross(up));
        if(primary.contains("ship_ascend") || primary.contains("ship_descend")) res = removeAxis(res, up);
        return res;
    }

    public static Vec3 remainingTorque(Vec3 request, Set<String> primary, Vec3 forward, Vec3 up){
        Vec3 res = request;
        if(primary.stream().anyMatch(val -> val.startsWith("ship_yaw") || val.equals("ship_pan"))) res = removeAxis(res, up);
        if(primary.stream().anyMatch(val -> val.startsWith("ship_pitch") || val.equals("ship_tilt"))) res = removeAxis(res, forward.cross(up));
        if(primary.stream().anyMatch(val -> val.startsWith("ship_roll"))) res = removeAxis(res, forward);
        return res;
    }

    // Let active steering or authored banking own its axes while leveling the remaining axes
    public static Vec3 remainingTorque(Vec3 request, Vec3 drivenTorque, Vec3 forward, Vec3 up){
        Vec3 res = request;
        for(Vec3 axis : java.util.List.of(up, forward.cross(up), forward)){
            if(Math.abs(drivenTorque.dot(axis.normalize())) > 1.0E-5D) res = removeAxis(res, axis);
        }
        return res;
    }

    // Keep primary lift support without adding a second source's support along the same axis
    public static Vec3 withoutSharedCompensation(Vec3 request, Vec3 optionalCompensation, Vec3 primaryCompensation){
        if(primaryCompensation.lengthSqr() < 1.0E-12D) return request;
        Vec3 axis = primaryCompensation.normalize();
        return request.subtract(axis.scale(optionalCompensation.dot(axis)));
    }

    // Preserve signed active intent on shared axes while applying environmental support once
    public static ScmWrenchSourceRegistry.Wrench additionalWrench(ScmWrenchSourceRegistry.Wrench request,
                                                                 Vec3 primaryCompensation){
        if(!request.active()) return ScmWrenchSourceRegistry.Wrench.NONE;
        return new ScmWrenchSourceRegistry.Wrench(true,
                withoutSharedCompensation(request.force(), request.compensationForce(), primaryCompensation),
                request.torque(),
                withoutSharedCompensation(request.compensationForce(), request.compensationForce(), primaryCompensation), request.rotationActions());
    }

    private static Vec3 removeAxis(Vec3 val, Vec3 axis){
        Vec3 direction = axis.normalize();
        return val.subtract(direction.scale(val.dot(direction)));
    }
}
