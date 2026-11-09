package com.rieno.gadgetsandgizmos.lib.scm;

// Convert a tracked connector or altitude error to a bounded native height command
public final class ScmPilotHeightControl{
    // Prevent height controller instances
    private ScmPilotHeightControl(){}

    // Keep a native raise/lower input quiet inside the target tolerance
    public static int direction(double error, double tolerance){
        if(!Double.isFinite(error)) return 0;
        double band = Double.isFinite(tolerance) ? Math.max(0.025D, tolerance) : 0.025D;
        return error > band ? 1 : error < -band ? -1 : 0;
    }

    // Hold an absolute altitude while damping vertical motion within the native velocity limits
    public static double velocity(double error, double measuredSpeed, double tolerance, double upLimit, double downLimit){
        if(!Double.isFinite(error) || !Double.isFinite(upLimit) || !Double.isFinite(downLimit)) return 0;
        double speed = Double.isFinite(measuredSpeed) ? measuredSpeed : 0;
        double correction = direction(error, tolerance) == 0 ? 0 : error * 2.0D;
        return Math.clamp(correction - speed * 0.5D, -Math.max(0, downLimit), Math.max(0, upLimit));
    }
}
