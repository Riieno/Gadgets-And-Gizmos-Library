package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.LinkedHashMap;

// Build a live ship model and select feedback poles from available acceleration
public final class ScmAdaptiveStateModel{
    private static final int AXES = 6;
    private static final int STATES = 12;
    private static final double EPSILON = 1.0E-12D;
    private static final int MAX_CACHED_MODELS = 8;
    private static final int MAX_CACHED_ACTUATORS = 16_384;
    private static final int MAX_CACHED_AXES = 128;
    private static final ThreadLocal<Map<ModelKey, ScmAdaptiveStateModel>> MODELS =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(MAX_CACHED_MODELS, 0.75F, true));

    private final double[][] inputMatrix;
    private final double[] trim;
    private final double[][] columns;
    private final double[] limits;
    private final double[] lowerLimits;
    private final double[] magnitudes;
    private final int[] activeColumns;
    private final double[] controls;
    private final double[] achieved = new double[AXES];
    private final double[] target = new double[AXES];
    private final double[] minimum = new double[AXES];
    private final double[] maximum = new double[AXES];
    private final boolean available;
    private final Map<AxisKey, double[]> authority = new LinkedHashMap<>(MAX_CACHED_AXES, 0.75F, true);
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
        inputMatrix = new double[STATES][actuators.size()];
        trim = new double[actuators.size()];
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
        // Raising and lowering one throttle use opposite signs of the same acceleration column
        columns = new double[trim.length][AXES];
        limits = new double[columns.length];
        lowerLimits = new double[columns.length];
        magnitudes = new double[columns.length];
        controls = new double[columns.length];
        int[] active = new int[columns.length];
        int count = 0;
        for(int idx = 0; idx < trim.length; idx++){
            double upper = 1.0D - trim[idx];
            limits[idx] = upper > EPSILON ? upper : 0.0D;
            lowerLimits[idx] = trim[idx] > EPSILON ? -trim[idx] : 0.0D;
            for(int row = 0; row < AXES; row++){
                double effect = inputMatrix[row + AXES][idx]
                        * (row < 3 ? 1.0D : this.characteristicLength);
                columns[idx][row] = effect;
            }
        }
        for(int idx = 0; idx < columns.length; idx++){
            for(int row = 0; row < AXES; row++){
                magnitudes[idx] += columns[idx][row] * columns[idx][row];
            }
            if((limits[idx] > 0.0D || lowerLimits[idx] < 0.0D)
                    && magnitudes[idx] > 1.0E-24D) active[count++] = idx;
        }
        activeColumns = Arrays.copyOf(active, count);
        boolean hasInput = false;
        for(int idx = 0; idx < trim.length; idx++){
            for(int row = 6; row < STATES; row++){
                if(Math.abs(inputMatrix[row][idx]) > EPSILON) hasInput = true;
            }
        }
        available = hasInput;
        for(int idx : activeColumns){
            for(int row = 0; row < AXES; row++){
                double lower = columns[idx][row] * lowerLimits[idx];
                double upper = columns[idx][row] * limits[idx];
                minimum[row] += Math.min(lower, upper);
                maximum[row] += Math.max(lower, upper);
            }
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

    // Include steering response while leaving the provider's nonlinear allocation constraints intact
    public static List<Actuator> forceActuators(ScmPrecisionAllocator.Unit unit, double mass,
            com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi.Tensor inverseInertia, double trim){
        double scale = 1.0D / positive(mass);
        List<Actuator> res = new java.util.ArrayList<>(3);
        res.add(new Actuator(unit.force().scale(scale), inverseInertia.transform(unit.torque()), trim));
        for(Vec3 lateral : ScmThrustGeometry.steeringForces(unit.force(), unit.coneDegrees())){
            Vec3 span = lateral.scale(2.0D);
            res.add(new Actuator(span.scale(scale), inverseInertia.transform(unit.momentArm().cross(span)), 0.5D));
        }
        return List.copyOf(res);
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
        List<Actuator> inputs = actuators == null ? List.of() : List.copyOf(actuators);
        ModelKey key = new ModelKey(inputs, positive(tickSeconds), positive(linearTolerance),
                positive(angularTolerance), positive(characteristicLength));
        Map<ModelKey, ScmAdaptiveStateModel> models = MODELS.get();
        ScmAdaptiveStateModel model = models.get(key);
        if(model != null) return model;
        model = new ScmAdaptiveStateModel(inputs, tickSeconds, linearTolerance,
                angularTolerance, characteristicLength);
        if(inputs.size() <= MAX_CACHED_ACTUATORS){
            int count = inputs.size();
            for(ModelKey cached : models.keySet()) count += cached.actuators().size();
            while(!models.isEmpty() && (models.size() >= MAX_CACHED_MODELS || count > MAX_CACHED_ACTUATORS)){
                ModelKey oldest = models.keySet().iterator().next();
                count -= oldest.actuators().size();
                models.remove(oldest);
            }
            models.put(key, model);
        }
        return model;
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
        return available;
    }

    // Return the linearized state matrix for position, attitude and their rates
    public double[][] stateMatrix(){
        double[][] res = new double[STATES][STATES];
        for(int axis = 0; axis < AXES; axis++) res[axis][axis + AXES] = 1.0D;
        return res;
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
                ignored -> new double[]{Double.NaN, Double.NaN});
        int idx = positive ? 1 : 0;
        if(Double.isNaN(capacity[idx])) capacity[idx] = feasibleAuthority(requested, angular, positive);
        if(authority.size() > MAX_CACHED_AXES) authority.remove(authority.keySet().iterator().next());
        return capacity[idx];
    }

    private record AxisKey(double x, double y, double z, boolean angular){}

    // Reuse models only when every live actuator and sampling parameter is identical
    private record ModelKey(List<Actuator> actuators, double tickSeconds, double linearTolerance,
            double angularTolerance, double characteristicLength){}

    // Find thrust headroom that accelerates the requested axis without unwanted motion
    private double feasibleAuthority(Vec3 axis, boolean angular, boolean positive){
        if(trim.length == 0) return 0.0D;
        double sign = positive ? 1.0D : -1.0D;
        double upper = 0.0D;
        for(int idx = 0; idx < trim.length; idx++){
            int offset = angular ? 9 : 6;
            double projection = axis.x * inputMatrix[offset][idx]
                    + axis.y * inputMatrix[offset + 1][idx]
                    + axis.z * inputMatrix[offset + 2][idx];
            upper += Math.max(0.0D, projection * sign) * (1.0D - trim[idx])
                    + Math.max(0.0D, -projection * sign) * trim[idx];
        }
        // Component bounds give a tighter starting interval than the summed projection.
        // Allow the solver's existing ten-percent residual tolerance at the boundary.
        int rowOffset = angular ? 3 : 0;
        double scale = angular ? characteristicLength : 1.0D;
        double[] components = {axis.x * sign * scale, axis.y * sign * scale, axis.z * sign * scale};
        for(int row = 0; row < 3; row++){
            double component = components[row];
            double constrained = Math.abs(component) - scale * 0.1D;
            if(constrained <= EPSILON) continue;
            double bound = component > 0.0D ? maximum[rowOffset + row] : -minimum[rowOffset + row];
            upper = Math.min(upper, bound / constrained);
        }
        if(upper <= EPSILON) return 0.0D;
        double lower = 0.0D;
        // Small holding throttles can leave a usable interval far below full engine output
        for(int step = 0; step < 32; step++){
            double candidate = step == 0 ? upper : (lower + upper) * 0.5D;
            if(canProduce(axis, angular, sign * candidate)){
                lower = candidate;
                if(step == 0) break;
            }else upper = candidate;
            if(upper <= EPSILON || lower > 0.0D && upper - lower <= upper / 128.0D) break;
        }
        return lower;
    }

    // Solve one signed throttle change per actuator against all six acceleration axes
    private boolean canProduce(Vec3 axis, boolean angular, double amount){
        Arrays.fill(target, 0.0D);
        int offset = angular ? 3 : 0;
        double scale = angular ? characteristicLength : 1.0D;
        target[offset] = axis.x * amount * scale;
        target[offset + 1] = axis.y * amount * scale;
        target[offset + 2] = axis.z * amount * scale;
        double squaredTarget = 0.0D;
        for(double val : target) squaredTarget += val * val;
        if(squaredTarget <= 0.0D) return false;
        double tolerance = squaredTarget * 0.01D;
        // Reject only targets whose component bounds already prove excessive residual
        double boundedError = 0.0D;
        for(int row = 0; row < AXES; row++){
            double error = target[row] < minimum[row] ? minimum[row] - target[row]
                    : Math.max(0.0D, target[row] - maximum[row]);
            boundedError += error * error;
        }
        if(boundedError > tolerance + Math.max(boundedError, tolerance) * 1.0E-10D) return false;
        Arrays.fill(controls, 0.0D);
        Arrays.fill(achieved, 0.0D);
        double x = 0.0D, y = 0.0D, z = 0.0D;
        double rx = 0.0D, ry = 0.0D, rz = 0.0D;
        for(int iteration = 0; iteration < 96; iteration++){
            double largestChange = 0.0D;
            for(int idx : activeColumns){
                double[] column = columns[idx];
                double gradient = 0.0D;
                gradient += column[0] * (x - target[0]);
                gradient += column[1] * (y - target[1]);
                gradient += column[2] * (z - target[2]);
                gradient += column[3] * (rx - target[3]);
                gradient += column[4] * (ry - target[4]);
                gradient += column[5] * (rz - target[5]);
                double next = clamp(controls[idx] - gradient / magnitudes[idx],
                        lowerLimits[idx], limits[idx]);
                double change = next - controls[idx];
                if(change == 0.0D) continue;
                controls[idx] = next;
                largestChange = Math.max(largestChange, Math.abs(change));
                x += column[0] * change;
                y += column[1] * change;
                z += column[2] * change;
                rx += column[3] * change;
                ry += column[4] * change;
                rz += column[5] * change;
            }
            achieved[0] = x;
            achieved[1] = y;
            achieved[2] = z;
            achieved[3] = rx;
            achieved[4] = ry;
            achieved[5] = rz;
            double squaredError = 0.0D;
            for(int row = 0; row < AXES; row++){
                double error = achieved[row] - target[row];
                squaredError += error * error;
            }
            // Coordinate updates only reduce error, so an accepted wrench needs no further refinement
            if(squaredError <= tolerance) return true;
            if((iteration & 3) == 0 && infeasible(target, squaredError, tolerance)) return false;
            if(largestChange <= EPSILON) break;
        }
        double squaredError = 0.0D;
        for(int row = 0; row < AXES; row++){
            squaredError += Math.pow(achieved[row] - target[row], 2.0D);
        }
        return squaredError <= tolerance;
    }

    // Bound the best possible residual before spending more iterations on an impossible wrench
    private boolean infeasible(double[] target, double squaredError, double tolerance){
        double lower = squaredError;
        double x = achieved[0] - target[0], y = achieved[1] - target[1], z = achieved[2] - target[2];
        double rx = achieved[3] - target[3], ry = achieved[4] - target[4], rz = achieved[5] - target[5];
        for(int idx : activeColumns){
            double[] column = columns[idx];
            double gradient = 0.0D;
            gradient += column[0] * x;
            gradient += column[1] * y;
            gradient += column[2] * z;
            gradient += column[3] * rx;
            gradient += column[4] * ry;
            gradient += column[5] * rz;
            lower += 2.0D * gradient * (gradient > 0.0D
                    ? lowerLimits[idx] - controls[idx] : limits[idx] - controls[idx]);
        }
        return lower > tolerance + Math.max(squaredError, tolerance) * 1.0E-10D;
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

    // Select recovery speed independently of the available propulsion force
    public Response feedback(Vec3 axis, boolean angular, double error, double rate, double strength){
        double positive = authority(axis, angular, true);
        double negative = authority(axis, angular, false);
        double acceleration = ScmStateFeedback.recoveryAcceleration(error, rate, strength,
                tickSeconds, positive, negative);
        double pole = -ScmControlInputs.strength(strength) / tickSeconds;
        return new Response(acceleration, pole, pole, positive, negative);
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

    // Recover attitude at an explicit speed while respecting directional torque capacity
    public Vec3 angularFeedback(Vec3 error, Vec3 rate, double strength){
        if(ScmControlInputs.strength(strength) == 0.0D) return Vec3.ZERO;
        return vectorFeedback(error, rate, true, strength, true);
    }

    private Vec3 vectorFeedback(Vec3 error, Vec3 rate, boolean angular){
        return vectorFeedback(error, rate, angular, 1.0D, false);
    }

    // Preserve diagonal control directions and damp motion outside the target axis
    private Vec3 vectorFeedback(Vec3 error, Vec3 rate, boolean angular, double strength, boolean recovery){
        Vec3 target = finite(error);
        Vec3 currentRate = finite(rate);
        Vec3 aim = target.subtract(currentRate.scale(tickSeconds));
        if(aim.lengthSqr() <= EPSILON) return Vec3.ZERO;
        Vec3 axis = aim.normalize();
        Vec3 result = axis.scale((recovery
                ? feedback(axis, angular, target.dot(axis), currentRate.dot(axis), strength)
                : feedback(axis, angular, target.dot(axis), currentRate.dot(axis))).acceleration());
        Vec3 remainingRate = currentRate.subtract(
                axis.scale(currentRate.dot(axis)));
        if(remainingRate.lengthSqr() > EPSILON){
            Vec3 dampingAxis = remainingRate.normalize();
            result = result.add(dampingAxis.scale((recovery
                    ? feedback(dampingAxis, angular, 0.0D, remainingRate.length(), strength)
                    : feedback(dampingAxis, angular, 0.0D, remainingRate.length())).acceleration()));
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
