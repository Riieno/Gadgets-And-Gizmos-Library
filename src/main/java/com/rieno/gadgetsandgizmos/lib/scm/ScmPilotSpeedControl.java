package com.rieno.gadgetsandgizmos.lib.scm;

// Regulate native speed through discrete gears and a bounded held-input duty cycle
public final class ScmPilotSpeedControl{
    // Retain fractional effort across ticks without consuming it during repeated reads
    private double phase;
    private long lastTick = Long.MIN_VALUE;
    private boolean held;

    // Select the lowest gear which can reach the requested speed
    public static int gear(double requestedSpeed, double[] speeds){
        if(speeds == null || speeds.length == 0) return 0;
        double requested = Double.isFinite(requestedSpeed) ? Math.max(0, requestedSpeed) : 0;
        for(int idx = 0; idx < speeds.length; idx++){
            if(Double.isFinite(speeds[idx]) && speeds[idx] >= requested) return idx;
        }
        return speeds.length - 1;
    }

    // Maintain a speed setpoint instead of dropping to a low gear when velocity error reaches zero
    public boolean drive(long tick, double requestedSpeed, double measuredSpeed, double gearSpeed){
        if(!Double.isFinite(requestedSpeed) || requestedSpeed <= 0 || !Double.isFinite(gearSpeed) || gearSpeed <= 0){
            phase = 0;
            held = false;
            lastTick = tick;
            return false;
        }
        double speed = Double.isFinite(measuredSpeed) ? measuredSpeed : 0;
        double effort = Math.clamp((requestedSpeed + (requestedSpeed - speed) * 0.5D) / gearSpeed, 0, 1);
        if(tick == lastTick) return held;
        if(tick < lastTick || lastTick == Long.MIN_VALUE) phase = 1.0D;
        lastTick = tick;
        phase += effort;
        held = effort > 0 && phase >= 1.0D;
        if(held) phase -= 1.0D;
        phase = Math.clamp(phase, 0, 1);
        return held;
    }
}
