package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Solve an ordered rotary chain in one shared coordinate frame
public final class ScmArticulatedIk {
    private ScmArticulatedIk(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Return absolute motor targets from measured pivots, axes and native angles
    public static Solution solve(Vec3 end, Vec3 target, List<Joint> joints){
        if(end == null || target == null || !Double.isFinite(end.lengthSqr())
                || !Double.isFinite(target.lengthSqr())) throw new IllegalArgumentException("Invalid end-effector position");
        if(joints == null || joints.isEmpty()) return new Solution(List.of(), target.subtract(end));
        List<Joint> chain = List.copyOf(joints);
        double[] straight = new double[chain.size()];
        double[] seeded = new double[chain.size()];
        for(int idx = 0; idx < chain.size(); idx++){
            Joint joint = chain.get(idx);
            straight[idx] = boundedDelta(joint, 0.0D);
            seeded[idx] = boundedDelta(joint, joint.preferredAngle() - joint.angle());
        }
        // A preferred bend gives a straight chain a direction out of its singular pose.
        refine(end, target, chain, straight);
        refine(end, target, chain, seeded);
        double[] selected = score(end, target, chain, seeded) < score(end, target, chain, straight)
                ? seeded : straight;
        List<Double> angles = new ArrayList<>(chain.size());
        for(int idx = 0; idx < chain.size(); idx++) angles.add(chain.get(idx).angle() + selected[idx]);
        return new Solution(List.copyOf(angles), target.subtract(pose(end, chain, selected).end()));
    }

    // Evaluate the end effector after changing the measured joint angles
    public static Vec3 forward(Vec3 end, List<Joint> joints, List<Double> angles){
        if(joints.size() != angles.size()) throw new IllegalArgumentException("Joint target count differs");
        double[] deltas = new double[joints.size()];
        for(int idx = 0; idx < deltas.length; idx++) deltas[idx] = angles.get(idx) - joints.get(idx).angle();
        return pose(end, joints, deltas).end();
    }

    // Restrict a segment to a cone around a reference direction in its hinge plane
    public static Joint restrictSwing(Joint joint, Vec3 child, Vec3 reference, double halfAngle){
        if(!Double.isFinite(halfAngle) || halfAngle <= 0 || halfAngle >= Math.PI * 0.5D){
            throw new IllegalArgumentException("Invalid swing cone");
        }
        Vec3 segment = child.subtract(joint.position());
        Vec3 axis = joint.axis();
        segment = segment.subtract(axis.scale(segment.dot(axis)));
        Vec3 direction = reference.subtract(axis.scale(reference.dot(axis)));
        if(segment.lengthSqr() < 1.0E-12D || direction.lengthSqr() < 1.0E-12D) return joint;
        double correction = Math.atan2(axis.dot(segment.cross(direction)), segment.dot(direction));
        double center = joint.angle() + correction;
        double min = Math.max(joint.minAngle(), center - halfAngle);
        double max = Math.min(joint.maxAngle(), center + halfAngle);
        if(min > max) throw new IllegalArgumentException("Swing cone conflicts with joint limits");
        return new Joint(joint.position(), axis, joint.angle(), min, max,
                Mth.clamp(joint.preferredAngle(), min, max));
    }

    // Convert a forward-facing knee bend into the measured motor's signed native angle
    public static double preferredBendAngle(Vec3 hip, Vec3 knee, Vec3 end, Vec3 axis,
            Vec3 forward, double angle, double bend){
        return preferredBendAngle(hip, knee, end, axis, new Vec3(0, 1, 0), forward, angle, bend);
    }

    // Keep the knee branch tied to gravity rather than the moving shin
    public static double preferredBendAngle(Vec3 hip, Vec3 knee, Vec3 end, Vec3 axis,
            Vec3 up, Vec3 forward, double angle, double bend){
        Vec3 normalizedAxis = axis.normalize();
        Vec3 upper = knee.subtract(hip);
        Vec3 lower = end.subtract(knee);
        upper = upper.subtract(normalizedAxis.scale(upper.dot(normalizedAxis))).normalize();
        lower = lower.subtract(normalizedAxis.scale(lower.dot(normalizedAxis))).normalize();
        double flex = Math.atan2(normalizedAxis.dot(upper.cross(lower)), upper.dot(lower));
        double sign = normalizedAxis.dot(up.cross(forward)) >= 0.0D ? 1.0D : -1.0D;
        return angle + sign * bend - flex;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Recompute the chain after each damped correction instead of integrating stale Jacobians
    private static void refine(Vec3 end, Vec3 target, List<Joint> joints, double[] deltas){
        for(int iteration = 0; iteration < 64; iteration++){
            Pose pose = pose(end, joints, deltas);
            Vec3 error = target.subtract(pose.end());
            if(error.lengthSqr() < 1.0E-8D) return;
            Vec3[] columns = new Vec3[joints.size()];
            double[][] matrix = {{0.0025D, 0.0D, 0.0D}, {0.0D, 0.0025D, 0.0D}, {0.0D, 0.0D, 0.0025D}};
            for(int idx = 0; idx < joints.size(); idx++){
                Vec3 column = pose.axes()[idx].cross(pose.end().subtract(pose.pivots()[idx]));
                columns[idx] = column;
                double[] values = {column.x, column.y, column.z};
                for(int row = 0; row < 3; row++){
                    for(int col = 0; col < 3; col++) matrix[row][col] += values[row] * values[col];
                }
            }
            Vec3 correction = solveMatrix(matrix, error);
            double[] step = new double[deltas.length];
            for(int idx = 0; idx < step.length; idx++){
                step[idx] = Mth.clamp(columns[idx].dot(correction), -0.25D, 0.25D);
            }
            boolean improved = false;
            for(double scale = 1.0D; scale >= 0.0625D; scale *= 0.5D){
                double[] candidate = deltas.clone();
                for(int idx = 0; idx < candidate.length; idx++){
                    candidate[idx] = boundedDelta(joints.get(idx), deltas[idx] + step[idx] * scale);
                }
                if(pose(end, joints, candidate).end().distanceToSqr(target) < error.lengthSqr()){
                    System.arraycopy(candidate, 0, deltas, 0, deltas.length);
                    improved = true;
                    break;
                }
            }
            if(!improved) return;
        }
    }

    // Prefer the requested bend when both branches reach the same target
    private static double score(Vec3 end, Vec3 target, List<Joint> joints, double[] deltas){
        double score = pose(end, joints, deltas).end().distanceToSqr(target);
        for(int idx = 0; idx < deltas.length; idx++){
            double error = joints.get(idx).angle() + deltas[idx] - joints.get(idx).preferredAngle();
            score += error * error * 1.0E-6D;
        }
        return score;
    }

    // Rotate all downstream pivots and axes with their upstream joint
    private static Pose pose(Vec3 end, List<Joint> joints, double[] deltas){
        Vec3[] pivots = joints.stream().map(Joint::position).toArray(Vec3[]::new);
        Vec3[] axes = joints.stream().map(Joint::axis).toArray(Vec3[]::new);
        Vec3 res = end;
        for(int idx = 0; idx < joints.size(); idx++){
            Vec3 pivot = pivots[idx];
            Vec3 axis = axes[idx];
            res = pivot.add(rotate(res.subtract(pivot), axis, deltas[idx]));
            for(int next = idx + 1; next < joints.size(); next++){
                pivots[next] = pivot.add(rotate(pivots[next].subtract(pivot), axis, deltas[idx]));
                axes[next] = rotate(axes[next], axis, deltas[idx]);
            }
        }
        return new Pose(res, pivots, axes);
    }

    // Rotate a vector about a normalized axis using the right-hand convention
    private static Vec3 rotate(Vec3 vector, Vec3 axis, double angle){
        double cosine = Math.cos(angle);
        return vector.scale(cosine).add(axis.cross(vector).scale(Math.sin(angle)))
                .add(axis.scale(axis.dot(vector) * (1.0D - cosine)));
    }

    // Respect native angle limits throughout the nonlinear solve
    private static double boundedDelta(Joint joint, double delta){
        return Mth.clamp(joint.angle() + delta, joint.minAngle(), joint.maxAngle()) - joint.angle();
    }

    // Solve the positive definite damped system without rejecting small valid determinants
    private static Vec3 solveMatrix(double[][] matrix, Vec3 rhs){
        double[] values = {rhs.x, rhs.y, rhs.z};
        for(int pivot = 0; pivot < 3; pivot++){
            double divisor = matrix[pivot][pivot];
            for(int col = pivot; col < 3; col++) matrix[pivot][col] /= divisor;
            values[pivot] /= divisor;
            for(int row = 0; row < 3; row++){
                if(row == pivot) continue;
                double factor = matrix[row][pivot];
                for(int col = pivot; col < 3; col++) matrix[row][col] -= factor * matrix[pivot][col];
                values[row] -= factor * values[pivot];
            }
        }
        return new Vec3(values[0], values[1], values[2]);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                             TYPES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Define a measured joint and its bend preference in native radians
    public record Joint(Vec3 position, Vec3 axis, double angle, double minAngle,
                        double maxAngle, double preferredAngle){
        public Joint {
            if(position == null || axis == null || !Double.isFinite(position.lengthSqr())
                    || !Double.isFinite(axis.lengthSqr()) || axis.lengthSqr() < 1.0E-12D
                    || !Double.isFinite(angle) || !Double.isFinite(minAngle)
                    || !Double.isFinite(maxAngle) || !Double.isFinite(preferredAngle) || minAngle > maxAngle){
                throw new IllegalArgumentException("Invalid joint geometry or limits");
            }
            axis = axis.normalize();
            preferredAngle = Mth.clamp(preferredAngle, minAngle, maxAngle);
        }
    }

    // Store absolute targets and the remaining Cartesian error after forward evaluation
    public record Solution(List<Double> angles, Vec3 residual){}

    private record Pose(Vec3 end, Vec3[] pivots, Vec3[] axes){}
}
