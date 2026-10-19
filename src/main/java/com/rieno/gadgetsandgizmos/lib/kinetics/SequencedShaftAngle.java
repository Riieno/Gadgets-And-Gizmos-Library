package com.rieno.gadgetsandgizmos.lib.kinetics;

// Accumulate shaft turns while limiting each instruction to its requested travel
public final class SequencedShaftAngle{
    private double angleDegrees;
    private double remainingDegrees;

    // Begin a relative turn, or use positive infinity for continuous rotation
    public void start(double travelDegrees){
        if(Double.isNaN(travelDegrees) || travelDegrees < 0.0D){
            throw new IllegalArgumentException("Shaft travel must be nonnegative");
        }
        remainingDegrees = travelDegrees;
    }

    // Advance one game tick using the signed output speed
    public void advance(float rpm){
        if(!Float.isFinite(rpm) || rpm == 0.0F || remainingDegrees == 0.0D) return;
        double step = Math.min(Math.abs(rpm * 0.3D), remainingDegrees);
        angleDegrees += Math.copySign(step, rpm);
        remainingDegrees = Math.max(0.0D, remainingDegrees - step);
    }

    // Restore a saved output angle and the unfinished travel
    public void restore(double angleDegrees, double remainingDegrees){
        if(!Double.isFinite(angleDegrees)) throw new IllegalArgumentException("Shaft angle must be finite");
        start(remainingDegrees);
        this.angleDegrees = angleDegrees;
    }

    // Get the unwrapped output angle so reduction gears retain their phase
    public double angleDegrees(){
        return angleDegrees;
    }

    // Get the travel remaining in the current instruction
    public double remainingDegrees(){
        return remainingDegrees;
    }
}
