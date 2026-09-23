package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.jetbrains.annotations.Nullable;

/**
 * Classifies SCM commands which require a direction-independent speed-control
 * group alongside their directional propulsion allocation.
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
