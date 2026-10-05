package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

import java.util.List;

// Allocate physical force and torque while retaining small precision actuators
public final class ScmPrecisionAllocator{
    private static final int AXES = 6;
    private static final int ITERATIONS = 320;

    private ScmPrecisionAllocator(){}

    // Store the full-output force and torque of one independently controlled provider
    public record Unit(Vec3 force, Vec3 torque, boolean precision){
        public Unit{
            force = finite(force);
            torque = finite(torque);
        }
    }

    // Store the bounded control levels in the same order as the input units
    public record Allocation(double[] controls, boolean precisionOnly, double residual){
        public Allocation{
            controls = controls == null ? new double[0] : controls.clone();
        }

        @Override
        public double[] controls(){
            return controls.clone();
        }
    }

    // Convert a normalized six-axis request into physical force and torque
    public static Vec3 physicalForce(List<Unit> units, Vec3 demand){
        return physicalDemand(units, demand, false);
    }

    // Convert a normalized six-axis request into physical force and torque
    public static Vec3 physicalTorque(List<Unit> units, Vec3 demand){
        return physicalDemand(units, demand, true);
    }

    // Normalize an acceleration request against current physical force capacity
    public static Vec3 normalizedAcceleration(List<Unit> units, Vec3 acceleration, double mass){
        if(!Double.isFinite(mass) || mass <= 0.0D) return Vec3.ZERO;
        Vec3 force = finite(acceleration).scale(mass);
        return new Vec3(normalized(force.x, capacity(units, 0, force.x)),
                normalized(force.y, capacity(units, 1, force.y)),
                normalized(force.z, capacity(units, 2, force.z)));
    }

    // Use precision providers alone when they can produce the requested wrench
    public static Allocation allocate(List<Unit> units, Vec3 force, Vec3 torque,
                                      double[] previous){
        return allocate(units, force, torque, previous, true);
    }

    // Favor main propulsion during sustained travel while retaining precision control
    public static Allocation allocateCruise(List<Unit> units, Vec3 force, Vec3 torque,
                                            double[] previous){
        return allocateCruise(units, force, torque, force, previous);
    }

    // Exclude engines opposing travel when the remaining providers can meet the wrench
    public static Allocation allocateCruise(List<Unit> units, Vec3 force, Vec3 torque,
                                            Vec3 travelDirection, double[] previous){
        List<Unit> available = units == null ? List.of() : List.copyOf(units);
        if(available.isEmpty()) return new Allocation(new double[0], false, 0.0D);
        Vec3 direction = finite(travelDirection);
        if(direction.lengthSqr() <= 1.0E-12D)
            return allocate(available, force, torque, previous, false);
        direction = direction.normalize();
        boolean[] allowed = new boolean[available.size()];
        boolean excluded = false;
        for(int idx = 0; idx < available.size(); idx++){
            Unit unit = available.get(idx);
            allowed[idx] = unit.precision() || unit.force().dot(direction)
                    >= -1.0E-6D * unit.force().length();
            excluded |= !allowed[idx];
        }
        if(!excluded) return allocate(available, force, torque, previous, false);
        double[] target = components(finite(force), finite(torque));
        double[] directed = solve(available, target, previous, false, false, allowed);
        double directedError = residual(available, directed, target);
        double[] all = solve(available, target, previous, false, false, null);
        double allError = residual(available, all, target);
        return directedError <= allError + 0.03D
                ? new Allocation(directed, false, directedError)
                : new Allocation(all, false, allError);
    }

    private static Allocation allocate(List<Unit> units, Vec3 force, Vec3 torque,
                                       double[] previous, boolean preferPrecision){
        List<Unit> available = units == null ? List.of() : List.copyOf(units);
        if(available.isEmpty()) return new Allocation(new double[0], false, 0.0D);
        double[] target = components(finite(force), finite(torque));
        boolean hasPrecision = available.stream().anyMatch(unit ->
                unit.precision() && powered(unit));
        if(preferPrecision && hasPrecision){
            double[] precise = solve(available, target, previous, true, true, null);
            double error = residual(available, precise, target);
            if(error <= 0.03D) return new Allocation(precise, true, error);
        }
        double[] all = solve(available, target, previous, false, preferPrecision, null);
        return new Allocation(all, false, residual(available, all, target));
    }

    private static Vec3 physicalDemand(List<Unit> units, Vec3 request, boolean torque){
        Vec3 target = finite(request);
        int offset = torque ? 3 : 0;
        return new Vec3(component(target.x) * capacity(units, offset, target.x),
                component(target.y) * capacity(units, offset + 1, target.y),
                component(target.z) * capacity(units, offset + 2, target.z));
    }

    private static double capacity(List<Unit> units, int axis, double request){
        if(units == null) return 0.0D;
        double result = 0.0D;
        for(Unit unit : units){
            if(unit == null) continue;
            double value = axis < 3 ? component(unit.force(), axis)
                    : component(unit.torque(), axis - 3);
            result += request < 0.0D ? Math.max(0.0D, -value) : Math.max(0.0D, value);
        }
        return result;
    }

    // Refine bounded throttle levels without slowing every axis to the longest lever
    private static double[] solve(List<Unit> units, double[] target,
                                  double[] previous, boolean precisionOnly,
                                  boolean preferPrecision, boolean[] allowed){
        int count = units.size();
        boolean hasPrecision = units.stream().anyMatch(unit ->
                unit.precision() && powered(unit));
        double[][] columns = new double[count][AXES];
        double[] scale = new double[AXES];
        double[] control = new double[count];
        for(int idx = 0; idx < count; idx++){
            Unit unit = units.get(idx);
            if(!powered(unit) || precisionOnly && !unit.precision()
                    || allowed != null && !allowed[idx]) continue;
            columns[idx] = components(unit.force(), unit.torque());
            control[idx] = previous != null && idx < previous.length
                    && (preferPrecision || !unit.precision())
                    ? component(previous[idx]) : 0.0D;
            for(int axis = 0; axis < AXES; axis++) scale[axis] += Math.abs(columns[idx][axis]);
        }
        for(int axis = 0; axis < AXES; axis++) scale[axis] = Math.max(1.0E-6D,
                Math.max(Math.abs(target[axis]), scale[axis] * 0.1D));
        for(double[] column : columns){
            for(int axis = 0; axis < AXES; axis++){
                column[axis] /= scale[axis];
            }
        }
        double[] desired = new double[AXES];
        for(int axis = 0; axis < AXES; axis++) desired[axis] = target[axis] / scale[axis];
        double[] achieved = new double[AXES];
        for(int idx = 0; idx < count; idx++){
            for(int axis = 0; axis < AXES; axis++){
                achieved[axis] += columns[idx][axis] * control[idx];
            }
        }
        for(int iteration = 0; iteration < ITERATIONS; iteration++){
            double largestChange = 0.0D;
            for(int idx = 0; idx < count; idx++){
                Unit unit = units.get(idx);
                if(!powered(unit) || precisionOnly && !unit.precision()
                        || allowed != null && !allowed[idx]) continue;
                double prev = previous != null && idx < previous.length
                        && (preferPrecision || !unit.precision())
                        ? component(previous[idx]) : 0.0D;
                double penalty = !hasPrecision || unit.precision() == preferPrecision
                        ? 0.0002D : 0.015D;
                double gradient = penalty
                        * control[idx]
                        + 0.004D * (control[idx] - prev);
                double magnitude = penalty + 0.004D;
                for(int axis = 0; axis < AXES; axis++){
                    gradient += columns[idx][axis] * (achieved[axis] - desired[axis]);
                    magnitude += columns[idx][axis] * columns[idx][axis];
                }
                double next = Math.max(0.0D, Math.min(1.0D, control[idx] - gradient / magnitude));
                double change = next - control[idx];
                control[idx] = next;
                largestChange = Math.max(largestChange, Math.abs(change));
                for(int axis = 0; axis < AXES; axis++) achieved[axis] += columns[idx][axis] * change;
            }
            if(largestChange <= 1.0E-8D) break;
        }
        return control;
    }

    private static double residual(List<Unit> units, double[] controls, double[] target){
        double[] actual = new double[AXES];
        double[] available = new double[AXES];
        for(int idx = 0; idx < units.size(); idx++){
            double[] column = components(units.get(idx).force(), units.get(idx).torque());
            for(int axis = 0; axis < AXES; axis++){
                actual[axis] += column[axis] * controls[idx];
                available[axis] += Math.abs(column[axis]);
            }
        }
        double largest = 0.0D;
        for(int axis = 0; axis < AXES; axis++){
            double scale = Math.max(1.0E-6D,
                    Math.max(Math.abs(target[axis]), available[axis] * 0.02D));
            largest = Math.max(largest, Math.abs(actual[axis] - target[axis]) / scale);
        }
        return largest;
    }

    private static double[] components(Vec3 force, Vec3 torque){
        return new double[]{force.x, force.y, force.z, torque.x, torque.y, torque.z};
    }

    private static boolean powered(Unit unit){
        return unit.force().lengthSqr() > 1.0E-12D
                || unit.torque().lengthSqr() > 1.0E-12D;
    }

    private static double component(Vec3 vector, int axis){
        return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z;
    }

    private static double component(double value){
        return Double.isFinite(value) ? Math.max(-1.0D, Math.min(1.0D, value)) : 0.0D;
    }

    private static double normalized(double force, double capacity){
        return capacity <= 1.0E-9D ? 0.0D : component(force / capacity);
    }

    private static Vec3 finite(Vec3 vector){
        if(vector == null) return Vec3.ZERO;
        return new Vec3(Double.isFinite(vector.x) ? vector.x : 0.0D,
                Double.isFinite(vector.y) ? vector.y : 0.0D,
                Double.isFinite(vector.z) ? vector.z : 0.0D);
    }
}
