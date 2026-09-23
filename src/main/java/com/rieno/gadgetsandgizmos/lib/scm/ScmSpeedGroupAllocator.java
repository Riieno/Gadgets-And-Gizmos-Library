package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// Allocate independent positive speed-control effort from mapped force and torque responses
public final class ScmSpeedGroupAllocator{
    private ScmSpeedGroupAllocator(){}

    public record Influence(Vec3 force, Vec3 torque){
        public Influence{
            force = finite(force);
            torque = finite(torque);
        }
    }

    public record Sample(double control, double effect){
        public Sample{
            control = Mth.clamp(finite(control), 0.0D, 1.0D);
            effect = Math.max(0.0D, finite(effect));
        }
    }

    // Normalize support force using only the outputs that these speed controls actually drive
    public static Vec3 normalizePhysicalForce(List<Influence> influences, Vec3 force){
        Vec3 requested = finite(force);
        double[] capacity = new double[3];
        double[] components = {requested.x, requested.y, requested.z};
        for(Influence influence : influences){
            double[] available = {influence.force().x, influence.force().y, influence.force().z};
            for(int axis = 0; axis < 3; axis++){
                if(available[axis] * components[axis] > 0.0D) capacity[axis] += Math.abs(available[axis]);
            }
        }
        for(int axis = 0; axis < 3; axis++){
            components[axis] = Mth.clamp(components[axis] / Math.max(1.0E-9D, capacity[axis]), -1.0D, 1.0D);
        }
        return new Vec3(components[0], components[1], components[2]);
    }

    // Smooth independent continuous controls while leaving explicit release to the host
    public static double regulatedControl(double prev, double requested, double maximumChange){
        double target = Mth.clamp(finite(requested), 0.0D, 1.0D);
        double previous = Mth.clamp(finite(prev), 0.0D, 1.0D);
        double change = Math.max(0.0D, finite(maximumChange));
        return previous + Mth.clamp(target - previous, -change, change);
    }

    // All outputs use the same normalized response axes, not one shared block signal
    public static double[] allocate(List<Influence> influences, Vec3 force, Vec3 torque, double maximumPower){
        int count = influences.size();
        double[] res = new double[count];
        double maximum = Mth.clamp(finite(maximumPower), 0.0D, 1.0D);
        if(count == 0 || maximum <= 0.0D) return res;
        double[][] columns = new double[count][6];
        double[] scales = new double[6];
        double[] residual = axes(finite(force), finite(torque));
        for(int idx = 0; idx < count; idx++){
            Influence influence = influences.get(idx);
            columns[idx] = axes(influence.force(), influence.torque());
            for(int axis = 0; axis < 6; axis++) scales[axis] += Math.abs(columns[idx][axis]);
        }
        for(int idx = 0; idx < count; idx++){
            for(int axis = 0; axis < 6; axis++){
                columns[idx][axis] /= Math.max(1.0E-9D, scales[axis]);
            }
        }
        double norm = 1.0E-9D;
        for(double[] column : columns){
            for(int axis = 0; axis < 6; axis++) norm += column[axis] * column[axis] * (axis < 3 ? 4.0D : 1.0D);
        }
        for(int pass = 0; pass < 128; pass++){
            double strongestChange = 0.0D;
            double[] delta = new double[count];
            for(int idx = 0; idx < count; idx++){
                double projection = 0.0D;
                for(int axis = 0; axis < 6; axis++){
                    double weight = axis < 3 ? 4.0D : 1.0D;
                    projection += columns[idx][axis] * residual[axis] * weight;
                }
                double next = Mth.clamp(res[idx] + projection / norm, 0.0D, maximum);
                delta[idx] = next - res[idx];
                res[idx] = next;
                strongestChange = Math.max(strongestChange, Math.abs(delta[idx]));
            }
            // Simultaneous updates share load equally between equivalent controllers
            for(int idx = 0; idx < count; idx++){
                for(int axis = 0; axis < 6; axis++) residual[axis] -= columns[idx][axis] * delta[idx];
            }
            if(strongestChange <= 1.0E-6D) break;
        }
        return res;
    }

    // Invert each controller's measured effect curve, ignoring zero-control baseline force
    public static double controlForEffort(List<Sample> samples, double effort){
        double requested = Mth.clamp(finite(effort), 0.0D, 1.0D);
        if(requested <= 1.0E-8D || samples.isEmpty()) return requested;
        double baseline = 0.0D;
        for(Sample sample : samples) if(sample.control() <= 1.0E-8D) baseline = Math.max(baseline, sample.effect());
        double maximum = baseline;
        for(Sample sample : samples) maximum = Math.max(maximum, sample.effect());
        if(maximum <= baseline + 1.0E-8D) return requested;
        double target = baseline + requested * (maximum - baseline);
        double lowerEffect = baseline;
        double lowerControl = 0.0D;
        double upperEffect = maximum;
        double upperControl = 1.0D;
        for(Sample sample : samples){
            if(sample.control() <= 1.0E-8D) continue;
            if(sample.effect() < target && sample.effect() > lowerEffect){
                lowerEffect = sample.effect();
                lowerControl = sample.control();
            }
            if(sample.effect() >= target && sample.effect() <= upperEffect){
                upperEffect = sample.effect();
                upperControl = sample.control();
            }
        }
        return Mth.clamp(Mth.lerp((target - lowerEffect) / Math.max(1.0E-9D, upperEffect - lowerEffect),
                lowerControl, upperControl), 0.0D, 1.0D);
    }

    private static double[] axes(Vec3 force, Vec3 torque){
        return new double[]{force.x, force.y, force.z, torque.x, torque.y, torque.z};
    }

    private static Vec3 finite(Vec3 val){
        return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z)
                ? val : Vec3.ZERO;
    }

    private static double finite(double val){ return Double.isFinite(val) ? val : 0.0D; }
}
