package com.rieno.gadgetsandgizmos.lib.scm;

// Retain a turn through an opposing heading and advance its analogue setpoint
public final class ScmGroundSteering{
    private double turn;
    private double steering;

    // Follow one physical yaw request; the host converts reverse steering at its actuator boundary
    public double update(double headingError, double curvature, double forwardSpeed, double yawRate,
                         double steeringFeedForward, double tickSeconds){
        double error = Double.isFinite(headingError) ? headingError : 0.0D;
        double angle = Math.abs(error);
        if(turn == 0.0D && angle >= Math.toRadians(120.0D)){
            turn = Math.abs(steering) > 0.05D ? Math.signum(steering)
                    : error < 0.0D ? -1.0D : 1.0D;
        }
        if(angle < Math.toRadians(60.0D)) turn = 0.0D;
        if(turn != 0.0D) error = Math.copySign(angle, turn);
        double target = ScmControlAxes.routeSteeringDemand(error, curvature, forwardSpeed,
                yawRate, steeringFeedForward);
        double step = Double.isFinite(tickSeconds) ? Math.max(0.0D, tickSeconds) * 1.6D : 0.0D;
        steering = ScmControlAxes.slewSteering(steering, target, step);
        return steering;
    }

    // Release the previous turn when a new command takes ownership
    public void reset(){
        turn = 0.0D;
        steering = 0.0D;
    }
}
