package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;

// Carry one timestamped orientation target and its live state
public final class OrientationPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // X angle in radians
    private final double xAngleRadians;
    // Z angle in radians
    private final double zAngleRadians;
    // Orientation direction
    private final Vec3 direction;
    // Tracks whether orientation is live
    private final boolean live;
    // Game time
    private final long gameTime;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the orientation
    public OrientationPayload(double xAngleRadians, double zAngleRadians, Vec3 direction, boolean live, long gameTime) {
        this.xAngleRadians = xAngleRadians;
        this.zAngleRadians = zAngleRadians;
        this.direction = direction;
        this.live = live;
        this.gameTime = gameTime;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the x angle radians
    public double getXAngleRadians() {
        return xAngleRadians;
    }

    // Get the z angle radians
    public double getZAngleRadians() {
        return zAngleRadians;
    }

    // Get the x angle degrees
    public double getXAngleDegrees() {
        return Math.toDegrees(xAngleRadians);
    }

    // Get the z angle degrees
    public double getZAngleDegrees() {
        return Math.toDegrees(zAngleRadians);
    }

    // Get the direction
    public Vec3 getDirection() {
        return direction;
    }

    // Check if this is live
    public boolean isLive() {
        return live;
    }

    // Get the game time
    public long getGameTime() {
        return gameTime;
    }
}
