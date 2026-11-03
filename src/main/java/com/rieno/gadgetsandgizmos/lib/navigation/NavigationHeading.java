package com.rieno.gadgetsandgizmos.lib.navigation;

// Track continuous turns and smooth heading corrections across both angle wrap boundaries
public final class NavigationHeading{
    private double degrees;
    private boolean initialized;

    // Convert signed or accumulated headings to the full compass range
    public static double normalize(double heading){
        if(!Double.isFinite(heading)) return 0;
        double angle = heading % 360;
        return angle < 0 ? angle + 360 : angle;
    }

    // Follow the nearest equivalent angle with a frame-rate independent 100 ms response
    public double update(double heading, double seconds){
        if(!Double.isFinite(heading)) return degrees;
        double angle = normalize(heading);
        if(!initialized){
            initialized = true;
            return degrees = angle;
        }
        double delta = normalize(angle - normalize(degrees) + 180) - 180;
        double blend = !Double.isFinite(seconds) || seconds <= 0 ? 0 : seconds >= 1 ? 1 : -Math.expm1(-seconds * 10);
        return degrees += delta * blend;
    }

    // Start at the next available heading when the tracked vehicle is unavailable
    public void reset(){
        initialized = false;
        degrees = 0;
    }
}
