package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.util.Mth;

// Keep rotary feedback and allowed arcs on a continuous angular branch
public final class ScmRotaryAngles {
    private static final double TURN = Math.PI * 2.0D;

    private ScmRotaryAngles(){}

    // Normalize radians to the native interval above -pi and including pi
    public static double wrap(double angle){
        if(!Double.isFinite(angle)) throw new IllegalArgumentException("Invalid rotary angle");
        double val = angle % TURN;
        if(val <= -Math.PI) val += TURN;
        if(val > Math.PI) val -= TURN;
        return val;
    }

    // Select the equivalent angle nearest the previous measurement
    public static double unwrapNear(double angle, double reference){
        if(!Double.isFinite(reference)) throw new IllegalArgumentException("Invalid rotary reference");
        return reference + wrap(angle - reference);
    }

    // Express a positive angular arc as an interval nearest the measured pose
    public static Interval interval(double start, double width, double reference){
        if(!Double.isFinite(width) || width < 0 || width > TURN){
            throw new IllegalArgumentException("Invalid rotary arc width");
        }
        double center = unwrapNear(start + width * 0.5D, reference);
        return new Interval(center - width * 0.5D, center + width * 0.5D);
    }

    // Store one continuous branch of an allowed angular arc
    public record Interval(double min, double max){
        public Interval {
            if(!Double.isFinite(min) || !Double.isFinite(max) || min > max){
                throw new IllegalArgumentException("Invalid rotary interval");
            }
        }

        // Clamp an equivalent target inside this branch without crossing the forbidden arc
        public double clamp(double angle){
            if(angle >= min && angle <= max) return angle;
            return Mth.clamp(unwrapNear(angle, (min + max) * 0.5D), min, max);
        }
    }
}
