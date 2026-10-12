package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.List;

// Bound pose changes so a moving joint can follow changing force requests smoothly
public final class ScmPoseControl{
    // Prevent construction
    private ScmPoseControl(){}

    // Advance an angular setpoint by at most the configured rate
    public static double advance(double current, double target, double degreesPerSecond, double tickSeconds){
        double prev = Double.isFinite(current) ? current : 0.0D;
        double next = Double.isFinite(target) ? target : prev;
        double step = Double.isFinite(degreesPerSecond) && Double.isFinite(tickSeconds)
                ? Math.max(0.0D, degreesPerSecond) * Math.max(0.0D, tickSeconds) : 0.0D;
        return prev + Mth.clamp(next - prev, -step, step);
    }

    // Interpolate calibrated steering poses independently of propulsion allocation
    public static SteeringPose steeringTarget(List<SteeringPose> poses, double demand){
        if(poses == null || poses.isEmpty()) return null;
        List<SteeringPose> samples = poses.stream().filter(val -> val != null
                && Double.isFinite(val.angleX()) && Double.isFinite(val.angleZ())
                && Double.isFinite(val.yawResponse())).toList();
        if(samples.isEmpty()) return null;
        SteeringPose neutral = samples.stream().min(Comparator.comparingDouble(val ->
                val.angleX() * val.angleX() + val.angleZ() * val.angleZ())).orElseThrow();
        double requested = Double.isFinite(demand) ? Mth.clamp(demand, -1.0D, 1.0D) : 0.0D;
        if(Math.abs(requested) <= 1.0E-6D) return neutral;
        double sign = Math.signum(requested);
        List<SteeringPose> branch = samples.stream().filter(val -> val == neutral
                || (val.yawResponse() - neutral.yawResponse()) * sign > 1.0E-9D)
                .sorted(Comparator.comparingDouble((SteeringPose val) ->
                        (val.yawResponse() - neutral.yawResponse()) * sign)
                        .thenComparingDouble(val -> Math.pow(val.angleX() - neutral.angleX(), 2)
                                + Math.pow(val.angleZ() - neutral.angleZ(), 2))).toList();
        if(branch.isEmpty()) return null;
        double capacity = (branch.getLast().yawResponse() - neutral.yawResponse()) * sign;
        if(capacity <= 1.0E-9D) return null;
        double target = Math.abs(requested) * capacity;
        SteeringPose prev = neutral;
        for(SteeringPose next : branch){
            double upper = (next.yawResponse() - neutral.yawResponse()) * sign;
            double lower = (prev.yawResponse() - neutral.yawResponse()) * sign;
            if(upper <= lower + 1.0E-9D) continue;
            if(upper >= target && upper > lower + 1.0E-9D){
                double fraction = Mth.clamp((target - lower) / (upper - lower), 0.0D, 1.0D);
                return new SteeringPose(Mth.lerp(fraction, prev.angleX(), next.angleX()),
                        Mth.lerp(fraction, prev.angleZ(), next.angleZ()),
                        Mth.lerp(fraction, prev.yawResponse(), next.yawResponse()));
            }
            prev = next;
        }
        return branch.getLast();
    }

    // Supply one joint pose and its measured physical yaw response
    public record SteeringPose(double angleX, double angleZ, double yawResponse){}
}
