package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.jetbrains.annotations.Nullable;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Classifies SCM speed-control routing and autonomous collision authority.
 */
public final class ScmCommandRouting {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String ACCELERATION_ACTION = "ship_accelerate";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SCM command routing
    private ScmCommandRouting() {
    }

    // Preserve the scheduling argument for existing consumers
    public static boolean allowsCollisionAvoidance(
            @Nullable String commandType, boolean requested,
            boolean scheduleControlled, boolean manualControlActive
    ){
        return allowsCollisionAvoidance(commandType, requested, manualControlActive);
    }

    // Keep connector capture under docking control instead of platform avoidance
    public static boolean allowsCollisionAvoidance(
            @Nullable String commandType, boolean requested, boolean manualControlActive
    ){
        if(commandType == null || !requested || manualControlActive) return false;
        return switch(commandType){
            case "ship_navigate", "ship_follow", "ship_climb" -> true;
            default -> false;
        };
    }

    // Separate live manual input from zero signals and retained Stop markers
    public static boolean isActiveManualControl(@Nullable String commandType, double amount, double strength){
        if(!isManualControl(commandType) || "ship_stop".equals(commandType)) return false;
        double val = "ship_brake".equals(commandType) || "ship_decelerate".equals(commandType)
                || "ship_hold_yaw".equals(commandType) ? strength : amount;
        return Double.isFinite(val) && Math.abs(val) > 1.0E-4D;
    }

    // Completed motion stops release manual priority without stopping the SCM
    public static boolean isActiveManualControl(@Nullable String commandType, double amount, double strength,
                                                 boolean completed){
        return !(completed && isMotionStop(commandType)) && isActiveManualControl(commandType, amount, strength);
    }

    // Identify unsigned strength controls whose zero value releases the persistent request
    public static boolean usesRecoveryStrength(@Nullable String commandType){
        if(commandType == null) return false;
        return switch(commandType){
            case "ship_hover", "ship_stabilize", "ship_decelerate", "ship_brake", "ship_hold_yaw" -> true;
            default -> false;
        };
    }

    // Identify transient requests that reduce motion without releasing ship control
    public static boolean isMotionStop(@Nullable String commandType){
        return "ship_brake".equals(commandType) || "ship_hold_yaw".equals(commandType)
                || "ship_decelerate".equals(commandType);
    }

    // Filter completed stop outputs without modifying the caller's action set
    public static Set<String> activeActions(@Nullable String commandType, boolean completed, Set<String> actions){
        if(!completed || !isMotionStop(commandType) || "ship_hold_yaw".equals(commandType)) return Set.copyOf(actions);
        return actions.stream().filter(action -> !action.equals(commandType)).collect(Collectors.toUnmodifiableSet());
    }

    // Identify direct flight inputs which take priority over avoidance
    public static boolean isManualControl(@Nullable String commandType){
        if(commandType == null) return false;
        return switch(commandType){
            case "ship_yaw", "ship_yaw_left", "ship_yaw_right", "ship_pan",
                 "ship_pitch", "ship_pitch_up", "ship_pitch_down", "ship_tilt",
                 "ship_roll", "ship_roll_left", "ship_roll_right",
                 "ship_accelerate", "ship_forward", "ship_reverse", "ship_backward",
                 "ship_strafe", "ship_strafe_left", "ship_strafe_right",
                 "ship_ascend", "ship_descend", "ship_jump", "ship_crouch",
                 "ship_decelerate", "ship_brake", "ship_hold_yaw", "ship_stop" -> true;
            default -> false;
        };
    }

    // Stop movement requests while preserving lift and attitude controls
    public static boolean supersededByMotionHold(String incoming, String active){
        if(incoming == null || active == null) return false;
        if("ship_brake".equals(incoming)) return "ship_brake".equals(active) || isMovementRequest(active);
        if("ship_brake".equals(active) && isMovementRequest(incoming)) return true;
        boolean incomingYaw = incoming.startsWith("ship_yaw") || "ship_pan".equals(incoming);
        boolean activeYaw = active.startsWith("ship_yaw") || "ship_pan".equals(active);
        return "ship_hold_yaw".equals(incoming) && (activeYaw || "ship_hold_yaw".equals(active))
                || incomingYaw && "ship_hold_yaw".equals(active);
    }

    // Give a new angle hold or direct rotation exclusive control of its held axis
    public static boolean supersededByMotionHold(String incoming, boolean incomingAngleHold,
                                                 String active, boolean activeAngleHold){
        if(supersededByMotionHold(incoming, active)) return true;
        if(!incomingAngleHold && !activeAngleHold) return false;
        ScmAngleControl.Axis axis = rotationAxis(incoming);
        return axis != null && axis == rotationAxis(active);
    }

    // Group the signed and directional controls which rotate the same craft axis
    private static @Nullable ScmAngleControl.Axis rotationAxis(String type){
        if(type == null) return null;
        return switch(type){
            case "ship_yaw", "ship_yaw_left", "ship_yaw_right", "ship_pan" -> ScmAngleControl.Axis.YAW;
            case "ship_pitch", "ship_pitch_up", "ship_pitch_down", "ship_tilt" -> ScmAngleControl.Axis.PITCH;
            case "ship_roll", "ship_roll_left", "ship_roll_right" -> ScmAngleControl.Axis.ROLL;
            default -> null;
        };
    }

    private static boolean isMovementRequest(String type){
        return switch(type){
            case "ship_accelerate", "ship_forward", "ship_reverse", "ship_backward",
                 "ship_strafe", "ship_strafe_left", "ship_strafe_right",
                 "ship_ascend", "ship_descend", "ship_jump", "ship_crouch",
                 "ship_navigate", "ship_follow", "ship_dock", "ship_climb", "ship_decelerate" -> true;
            default -> false;
        };
    }

    // Check whether a command requires the analogue speed-control group
    public static boolean requiresAccelerationControl(@Nullable String commandType) {
        if (commandType == null) {
            return false;
        }
        return switch (commandType) {
            case "ship_dock", "ship_navigate", "ship_follow",
                 "ship_hover", "ship_climb",
                 "ship_accelerate", "ship_forward", "ship_reverse", "ship_backward",
                 "ship_strafe", "ship_strafe_left", "ship_strafe_right",
                 "ship_ascend", "ship_descend", "ship_jump" -> true;
            default -> false;
        };
    }

    // Resolve authored engine power for hosts which need continuous lift
    public static double sustainingAccelerationPower(
            @Nullable String commandType, double propulsion, double strength
    ){
        if(commandType == null) return 0.0D;
        return switch(commandType){
            case "ship_hover", "ship_climb" -> ScmSpeedControl.accelerationSignal(strength, 0.0D, 0.0D);
            case "ship_navigate", "ship_follow", "ship_dock" -> {
                double requested = ScmSpeedControl.propulsionRequest(propulsion);
                yield requested < 0.0D ? 1.0D : requested;
            }
            default -> 0.0D;
        };
    }
}
