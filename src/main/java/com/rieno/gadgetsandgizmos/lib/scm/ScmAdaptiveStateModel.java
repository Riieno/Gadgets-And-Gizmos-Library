package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Build a live ship model and select feedback poles from available acceleration
public final class ScmAdaptiveStateModel{
    private static final int AXES = 6;
    private static final int STATES = 12;
    private static final double EPSILON = 1.0E-12D;

    private final double[][] stateMatrix;
    private final double[][] inputMatrix;
    private final double[] trim;
    private final Map<AxisKey, double[]> authority = new HashMap<>();
    private final double tickSeconds;
    private final double linearTolerance;
    private final double angularTolerance;
    private final double characteristicLength;

    private ScmAdaptiveStateModel(List<Actuator> actuators, double tickSeconds,
                                  double linearTolerance, double angularTolerance,
                                  double characteristicLength){
        this.tickSeconds = positive(tickSeconds);
        this.linearTolerance = positive(linearTolerance);
        this.angularTolerance = positive(angularTolerance);
        this.characteristicLength = positive(characteristicLength);
        stateMatrix = new double[STATES][STATES];
        inputMatrix = new double[STATES][actuators.size()];
        trim = new double[actuators.size()];
        for(int axis = 0; axis < AXES; axis++) stateMatrix[axis][axis + AXES] = 1.0D;
        for(int idx = 0; idx < actuators.size(); idx++){
            Actuator actuator = actuators.get(idx);
            trim[idx] = actuator.trim();
            Vec3 linear = actuator.linearAcceleration();
            Vec3 angular = actuator.angularAcceleration();
            inputMatrix[6][idx] = linear.x;
            inputMatrix[7][idx] = linear.y;
            inputMatrix[8][idx] = linear.z;
            inputMatrix[9][idx] = angular.x;
            inputMatrix[10][idx] = angular.y;
            inputMatrix[11][idx] = angular.z;
        }
    }

    // Describe one live actuator around the current holding throttle
    public record Actuator(Vec3 linearAcceleration, Vec3 angularAcceleration, double trim){
        public Actuator{
            linearAcceleration = finite(linearAcceleration);
            angularAcceleration = finite(angularAcceleration);
            trim = clamp(trim, 0.0D, 1.0D);
        }
    }

    // Store the requested acceleration and the poles selected for this axis
    public record Response(double acceleration, double firstPole, double secondPole,
                           double positiveAuthority, double negativeAuthority){}

    // Create a model in the same frame as its actuator acceleration vectors
    public static ScmAdaptiveStateModel sample(List<Actuator> actuators,
                                               double tickSeconds,
                                               double linearTolerance,
                                               double angularTolerance,
                                               double characteristicLength){
        return new ScmAdaptiveStateModel(actuators == null ? List.of() : List.copyOf(actuators),
                tickSeconds, linearTolerance, angularTolerance, characteristicLength);
    }

    // Use one block as the geometric scale when a host has no ship dimensions
    public static ScmAdaptiveStateModel sample(List<Actuator> actuators,
                                               double tickSeconds,
                                               double linearTolerance,
                                               double angularTolerance){
        return sample(actuators, tickSeconds, linearTolerance, angularTolerance, 1.0D);
    }

    // Check if this ship has at least one live acceleration input
    public boolean available(){
        for(int idx = 0; idx < inputMatrix[6].length; idx++){
            for(int axis = 6; axis < STATES; axis++){
                if(Math.abs(inputMatrix[axis][idx]) > EPSILON) return true;
            }
        }
        return false;
    }

    // Return the linearized state matrix for position, attitude and their rates
    public double[][] stateMatrix(){
        return copy(stateMatrix);
    }

    // Return the live actuator matrix in the same column order as the inputs
    public double[][] inputMatrix(){
        return copy(inputMatrix);
    }

    // Get remaining acceleration in one signed direction about the holding throttle
    public synchronized double authority(Vec3 axis, boolean angular, boolean positive){
        Vec3 direction = finite(axis);
        if(direction.lengthSqr() <= EPSILON) return 0.0D;
        direction = direction.normalize();
        Vec3 requested = direction;
        double[] capacity = authority.computeIfAbsent(
                new AxisKey(requested.x, requested.y, requested.z, angular),
                ignored -> new double[]{feasibleAuthority(requested, angular, false),
                        feasibleAuthority(requested, angular, true)});
        return capacity[positive ? 1 : 0];
    }

    private record AxisKey(double x, double y, double z, boolean angular){}

    // Find thrust headroom that accelerates the requested axis without unwanted motion
    private double feasibleAuthority(Vec3 axis, boolean angular, boolean positive){
        int count = trim.length * 2;
        if(count == 0) return 0.0D;
        double sign = positive ? 1.0D : -1.0D;
        double[][] columns = new double[count][AXES];
        double[] limits = new double[count];
        double upper = 0.0D;
        for(int idx = 0; idx < trim.length; idx++){
            int offset = angular ? 9 : 6;
            double projection = axis.x * inputMatrix[offset][idx]
                    + axis.y * inputMatrix[offset + 1][idx]
                    + axis.z * inputMatrix[offset + 2][idx];
            limits[idx * 2] = 1.0D - trim[idx];
            limits[idx * 2 + 1] = trim[idx];
            upper += Math.max(0.0D, projection * sign) * limits[idx * 2]
                    + Math.max(0.0D, -projection * sign) * limits[idx * 2 + 1];
            for(int row = 0; row < AXES; row++){
                double effect = inputMatrix[row + AXES][idx]
                        * (row < 3 ? 1.0D : characteristicLength);
                columns[idx * 2][row] = effect;
                columns[idx * 2 + 1][row] = -effect;
            }
        }
        if(upper <= EPSILON) return 0.0D;
        double lower = 0.0D;
        for(int step = 0; step < 8; step++){
            double candidate = step == 0 ? upper : (lower + upper) * 0.5D;
            if(canProduce(columns, limits, axis, angular, sign * candidate)){
                lower = candidate;
                if(step == 0) break;
            }else upper = candidate;
        }
        return lower;
    }

    // Solve bounded actuator changes against all six acceleration axes
    private boolean canProduce(double[][] columns, double[] limits, Vec3 axis,
                               boolean angular, double amount){
        double[] target = new double[AXES];
        int offset = angular ? 3 : 0;
        double scale = angular ? characteristicLength : 1.0D;
        target[offset] = axis.x * amount * scale;
        target[offset + 1] = axis.y * amount * scale;
        target[offset + 2] = axis.z * amount * scale;
        double[] controls = new double[limits.length];
        double[] achieved = new double[AXES];
        for(int iteration = 0; iteration < 96; iteration++){
            double largestChange = 0.0D;
            for(int idx = 0; idx < controls.length; idx++){
                if(limits[idx] <= EPSILON) continue;
                double gradient = 0.0D;
                double magnitude = 0.0D;
                for(int row = 0; row < AXES; row++){
                    gradient += columns[idx][row] * (achieved[row] - target[row]);
                    magnitude += columns[idx][row] * columns[idx][row];
                }
                if(magnitude <= 1.0E-24D) continue;
                double next = clamp(controls[idx] - gradient / magnitude,
                        0.0D, limits[idx]);
                double change = next - controls[idx];
                controls[idx] = next;
                largestChange = Math.max(largestChange, Math.abs(change));
                for(int row = 0; row < AXES; row++){
                    achieved[row] += columns[idx][row] * change;
                }
            }
            if(largestChange <= EPSILON) break;
        }
        double squaredError = 0.0D;
        double squaredTarget = 0.0D;
        for(int row = 0; row < AXES; row++){
            squaredError += Math.pow(achieved[row] - target[row], 2.0D);
            squaredTarget += target[row] * target[row];
        }
        return squaredTarget > 0.0D
                && squaredError <= squaredTarget * 0.01D;
    }

    // Place repeated stable poles from the ship's available acceleration and error
    public Response feedback(Vec3 axis, boolean angular, double error, double rate){
        double positive = authority(axis, angular, true);
        double negative = authority(axis, angular, false);
        double balanced = positive > EPSILON && negative > EPSILON
                ? Math.sqrt(positive * negative) : Math.max(positive, negative);
        if(balanced <= EPSILON) return new Response(0.0D, 0.0D, 0.0D,
                positive, negative);
        double displacement = Math.max(angular ? angularTolerance : linearTolerance,
                Math.abs(finite(error)) + finite(rate) * finite(rate) / (2.0D * balanced));
        double frequency = Math.min(Math.sqrt(balanced / displacement),
                1.0D / (4.0D * tickSeconds));
        ScmStateFeedback.Gains gains = ScmStateFeedback.lqrForResponse(1.0D / frequency);
        double requested = ScmStateFeedback.acceleration(error, rate, gains);
        return new Response(clamp(requested, -negative, positive),
                -frequency, -frequency, positive, negative);
    }

    // Track a requested rate with the acceleration this ship can currently supply
    public double velocityFeedback(Vec3 axis, boolean angular,
                                   double desiredRate, double rate, double tolerance){
        double error = finite(desiredRate) - finite(rate);
        double available = authority(axis, angular, error >= 0.0D);
        if(available <= EPSILON) return 0.0D;
        double distance = Math.max(finite(tolerance),
                angular ? angularTolerance : linearTolerance);
        double frequency = Math.min(Math.sqrt(available / distance),
                1.0D / (4.0D * tickSeconds));
        return clamp(error * frequency, -authority(axis, angular, false),
                authority(axis, angular, true));
    }

    // Apply the model to all three world-frame linear axes
    public Vec3 linearFeedback(Vec3 error, Vec3 velocity){
        return vectorFeedback(error, velocity, false);
    }

    // Track world-frame linear velocity using the ship's live authority
    public Vec3 linearVelocityFeedback(Vec3 desired, Vec3 velocity, double tolerance){
        Vec3 target = finite(desired);
        Vec3 current = finite(velocity);
        Vec3 error = target.subtract(current);
        if(error.lengthSqr() <= EPSILON) return Vec3.ZERO;
        Vec3 axis = error.normalize();
        return axis.scale(velocityFeedback(axis, false,
                target.dot(axis), current.dot(axis), tolerance));
    }

    // Apply the model to all three world-frame angular axes
    public Vec3 angularFeedback(Vec3 error, Vec3 rate){
        return vectorFeedback(error, rate, true);
    }

    // Preserve diagonal control directions and damp motion outside the target axis
    private Vec3 vectorFeedback(Vec3 error, Vec3 rate, boolean angular){
        Vec3 target = finite(error);
        Vec3 currentRate = finite(rate);
        Vec3 aim = target.subtract(currentRate.scale(tickSeconds));
        if(aim.lengthSqr() <= EPSILON) return Vec3.ZERO;
        Vec3 axis = aim.normalize();
        Vec3 result = axis.scale(feedback(axis, angular,
                target.dot(axis), currentRate.dot(axis)).acceleration());
        Vec3 remainingRate = currentRate.subtract(
                axis.scale(currentRate.dot(axis)));
        if(remainingRate.lengthSqr() > EPSILON){
            Vec3 dampingAxis = remainingRate.normalize();
            result = result.add(dampingAxis.scale(feedback(dampingAxis, angular,
                    0.0D, remainingRate.length()).acceleration()));
        }
        return result;
    }

    private static double[][] copy(double[][] matrix){
        double[][] result = new double[matrix.length][];
        for(int row = 0; row < matrix.length; row++) result[row] = matrix[row].clone();
        return result;
    }

    private static double positive(double value){
        if(!Double.isFinite(value) || value <= 0.0D){
            throw new IllegalArgumentException("Model intervals and tolerances must be positive");
        }
        return value;
    }

    private static double clamp(double value, double minimum, double maximum){
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : 0.0D;
    }

    private static double finite(double value){
        return Double.isFinite(value) ? value : 0.0D;
    }

    private static Vec3 finite(Vec3 value){
        if(value == null) return Vec3.ZERO;
        return new Vec3(finite(value.x), finite(value.y), finite(value.z));
    }
}
