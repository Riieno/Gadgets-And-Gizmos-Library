package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

// Reconcile solver velocity with successive poses sampled by a slower control loop
public final class ScmLinearMotionFeedback{
    private long tick = Long.MIN_VALUE;
    private Vec3 position = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private Vec3 feedback = Vec3.ZERO;

    // Remove impulse-phase offsets while extrapolating measured acceleration to the current sample
    public Vec3 sample(long tick, Vec3 position, Vec3 velocity, double tickSeconds){
        if(!valid(position) || !valid(velocity) || !Double.isFinite(tickSeconds) || tickSeconds <= 0.0D){
            reset();
            return Vec3.ZERO;
        }
        if(this.tick == tick) return feedback;
        Vec3 res = velocity;
        if(this.tick != Long.MIN_VALUE && tick - this.tick == 1){
            Vec3 measured = position.subtract(this.position).scale(1.0D / tickSeconds);
            Vec3 change = velocity.subtract(this.velocity);
            // Teleports and discontinuous pose corrections must not become movement commands
            if(measured.subtract(velocity).length() <= 4.0D + change.length()){
                res = measured.add(change.scale(0.5D));
            }
        }
        this.tick = tick;
        this.position = position;
        this.velocity = velocity;
        feedback = res;
        return res;
    }

    // Clear history when the controlled body or reference point changes
    public void reset(){
        tick = Long.MIN_VALUE;
        position = Vec3.ZERO;
        velocity = Vec3.ZERO;
        feedback = Vec3.ZERO;
    }

    private static boolean valid(Vec3 val){
        return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z);
    }
}
