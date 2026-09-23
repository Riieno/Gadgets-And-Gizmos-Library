package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.util.Mth;

/**
 * Resolves the normalized forward and yaw request used by differential SCM
 * drivetrains. Hosts supply their own calibrated actuator authority.
 */
public final class ScmTankSteering {
    private static final double YAW_DEADBAND = 0.08D;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the tank steering helper
    private ScmTankSteering(){
    }

    // Check whether configured kinetic groups describe a differential drivetrain
    public static boolean isConfigured(int longitudinalKineticGroups, int yawKineticGroups){
        return Math.max(0, longitudinalKineticGroups) > 0
                && Math.max(0, yawKineticGroups) > 0;
    }

    // Build the authored forward and physical right-yaw demand
    public static Demand demand(double forward, double yawRight){
        return new Demand(forward, yawRight);
    }

    // Remove steering noise which would otherwise alternate track sides on a straight route
    public static double stableYaw(double yawRight){
        double yaw = Mth.clamp(finite(yawRight), -1.0D, 1.0D);
        return Math.abs(yaw) <= YAW_DEADBAND ? 0.0D : yaw;
    }

    // Check whether the drivetrain should enter its zero-longitudinal pivot state
    public static boolean isTurning(double yawRight){
        return stableYaw(yawRight) != 0.0D;
    }

    // Store the differential drivetrain demand
    public record Demand(double forward, double yawRight){
        // Initialize the differential drivetrain demand
        public Demand{
            forward = Mth.clamp(finite(forward), -1.0D, 1.0D);
            yawRight = stableYaw(yawRight);
        }

        // Get the required kinetic control level
        public double controlLevel(){
            return Math.max(Math.abs(forward), Math.abs(yawRight));
        }

        // Score one calibrated kinetic direction for this request
        public double authorityScore(double forwardAuthority, double yawRightAuthority){
            return forward * Mth.clamp(finite(forwardAuthority), -1.0D, 1.0D)
                    + yawRight * Mth.clamp(finite(yawRightAuthority), -1.0D, 1.0D);
        }
    }

    // Normalize a finite scalar
    private static double finite(double value){
        return Double.isFinite(value) ? value : 0.0D;
    }
}
