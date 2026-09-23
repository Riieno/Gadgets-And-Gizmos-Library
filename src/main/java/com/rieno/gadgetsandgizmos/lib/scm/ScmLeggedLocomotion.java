package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Solve body-relative foot trajectories, support balance and three-joint leg inverse kinematics
public final class ScmLeggedLocomotion {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double EPSILON = 1.0E-6D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the legged locomotion solver
    private ScmLeggedLocomotion() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Solve the commanded leg and arm trajectories for one control tick
    public static Plan solve(Input input, Collection<Limb> limbs, Collection<Contact> contacts) {
        return solve(input, limbs, contacts, BodyMotion.NONE, null, null);
    }

    // Solve the commanded trajectories while retaining planted stance feet between ticks
    public static Plan solve(
            Input input, Collection<Limb> limbs, Collection<Contact> contacts,
            BodyMotion bodyMotion, GaitState gaitState
    ) {
        return solve(input, limbs, contacts, bodyMotion, gaitState, null);
    }

    // Solve the commanded trajectories with an explicit crouch or jump posture
    public static Plan solve(
            Input input, Collection<Limb> limbs, Collection<Contact> contacts,
            BodyMotion bodyMotion, GaitState gaitState, Posture posture
    ) {
        Input state = input == null ? Input.idle() : input;
        BodyMotion motion = bodyMotion == null ? BodyMotion.NONE : bodyMotion;
        Posture pose = posture == null
                ? new Posture(0.0D, state.jumpDemand(), false) : posture;
        List<Limb> ordered = limbs == null ? List.of() : limbs.stream()
                .filter(Objects::nonNull).filter(Limb::valid)
                .sorted(Comparator.comparing(Limb::id)).toList();
        Map<String, Contact> contactsById = new LinkedHashMap<>();
        if (contacts != null) {
            contacts.stream().filter(Objects::nonNull).forEach(contact ->
                    contactsById.putIfAbsent(contact.limbId(), contact));
        }
        List<Vec3> support = new ArrayList<>();
        for (Limb limb : ordered) {
            if (limb.kind() == LimbKind.ARM) continue;
            double phase = wrap(state.phase() + limb.phaseOffset());
            if (phase < state.swingFraction()) continue;
            Contact contact = contactsById.get(limb.id());
            support.add(contact != null && contact.grounded() ? contact.position()
                    : limb.hipPosition().add(limb.defaultFootPosition()));
        }
        Vec3 balance = balanceOffset(state, support, motion);
        Map<String, LimbTarget> targets = new LinkedHashMap<>();
        for (Limb limb : ordered) {
            if (limb.kind() == LimbKind.ARM) continue;
            double phase = wrap(state.phase() + limb.phaseOffset());
            boolean stance = phase >= state.swingFraction();
            Vec3 foot = footTarget(state, limb, phase, stance, balance, motion);
            Contact contact = contactsById.get(limb.id());
            if (contact != null && contact.grounded()) {
                double terrainOffset = contact.footPosition().y - limb.defaultFootPosition().y;
                foot = foot.add(0.0D, terrainOffset, 0.0D);
            }
            if (gaitState != null) {
                foot = gaitState.resolveFoot(limb, foot, stance, contact, motion);
            }
            foot = foot.add(postureOffset(state, limb, pose));
            foot = minimumKneeBend(limb, foot);
            targets.put(limb.id(), target(limb, foot, phase, stance));
        }
        for (Limb limb : ordered) {
            if (limb.kind() != LimbKind.ARM) continue;
            double phase = wrap(state.phase() + limb.phaseOffset());
            Vec3 arm = armTarget(state, limb, balance, phase);
            targets.put(limb.id(), target(limb, arm, phase, false));
        }
        if (gaitState != null) gaitState.retain(ordered);
        return new Plan(Map.copyOf(targets), balance, support.size(), supportMargin(state, support));
    }

    // Solve a three-revolute-joint limb for a body-local foot target
    public static JointAngles inverseKinematics(Limb limb, Vec3 footLocal) {
        if (limb == null || !limb.valid()) return JointAngles.ZERO;
        Vec3 foot = finite(footLocal);
        double yaw = Math.atan2(foot.x, foot.z);
        double radial = Math.max(0.0D, Math.sqrt(foot.x * foot.x + foot.z * foot.z) - limb.coxaLength());
        double vertical = -foot.y;
        double upper = limb.upperLength();
        double lower = limb.lowerLength();
        double reach = Mth.clamp(Math.sqrt(radial * radial + vertical * vertical),
                Math.abs(upper - lower) + EPSILON, upper + lower - EPSILON);
        double kneeInterior = Math.acos(Mth.clamp(
                (upper * upper + lower * lower - reach * reach) / (2.0D * upper * lower),
                -1.0D, 1.0D));
        double hipOffset = Math.acos(Mth.clamp(
                (upper * upper + reach * reach - lower * lower) / (2.0D * upper * reach),
                -1.0D, 1.0D));
        double hipPitch = Math.atan2(vertical, radial) - hipOffset;
        return new JointAngles(yaw + limb.yawOffset(), hipPitch + limb.hipOffset(),
                Math.PI - kneeInterior + limb.kneeOffset(), reach);
    }

    // Keep a foot-level ankle counter-rotating against the solved hip and knee pose
    public static double ankleCompensation(JointAngles desired, JointAngles rest) {
        JointAngles target = desired == null ? JointAngles.ZERO : desired;
        JointAngles reference = rest == null ? JointAngles.ZERO : rest;
        return Mth.clamp(-((target.hip() - reference.hip())
                + (target.knee() - reference.knee())), -Math.PI, Math.PI);
    }

    // Solve a redundant revolute chain with damped least-squares inverse kinematics
    public static RedundantIkSolution solveRedundantIk(
            Vec3 endEffectorPosition, Vec3 targetPosition,
            Collection<ArticulatedJoint> joints, double damping, double maximumDelta
    ) {
        Vec3 end = finite(endEffectorPosition);
        Vec3 error = finite(targetPosition).subtract(end);
        List<ArticulatedJoint> ordered = joints == null ? List.of() : joints.stream()
                .filter(Objects::nonNull).filter(ArticulatedJoint::valid).toList();
        if (ordered.isEmpty() || error.lengthSqr() <= EPSILON * EPSILON) {
            return new RedundantIkSolution(List.of(), error);
        }
        double[][] matrix = new double[3][3];
        List<Vec3> columns = new ArrayList<>();
        for (ArticulatedJoint joint : ordered) {
            Vec3 column = joint.axis().cross(end.subtract(joint.position())).scale(joint.weight());
            columns.add(column);
            matrix[0][0] += column.x * column.x;
            matrix[0][1] += column.x * column.y;
            matrix[0][2] += column.x * column.z;
            matrix[1][0] += column.y * column.x;
            matrix[1][1] += column.y * column.y;
            matrix[1][2] += column.y * column.z;
            matrix[2][0] += column.z * column.x;
            matrix[2][1] += column.z * column.y;
            matrix[2][2] += column.z * column.z;
        }
        double lambda = Math.max(EPSILON, finite(damping));
        double dampingSquared = lambda * lambda;
        for (int index = 0; index < 3; index++) matrix[index][index] += dampingSquared;
        Vec3 correction = solveMatrix(matrix, error);
        double limit = Math.max(EPSILON, finite(maximumDelta));
        List<Double> deltas = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            ArticulatedJoint joint = ordered.get(index);
            double delta = Mth.clamp(columns.get(index).dot(correction) * joint.weight(),
                    -Math.min(limit, joint.maximumDelta()), Math.min(limit, joint.maximumDelta()));
            deltas.add(delta);
        }
        return new RedundantIkSolution(List.copyOf(deltas), error);
    }

    // Get the requested foot target during stance or swing
    private static Vec3 footTarget(
            Input input, Limb limb, double phase, boolean stance,
            Vec3 balance, BodyMotion motion
    ) {
        Vec3 command = horizontal(input.command());
        double commandStrength = Mth.clamp(command.length(), 0.0D, 1.0D);
        Vec3 direction = commandStrength <= EPSILON ? Vec3.ZERO : command.normalize();
        double reach = limb.upperLength() + limb.lowerLength();
        double maximumStep = Mth.clamp(reach * 0.46D, 0.25D, 2.5D);
        Vec3 travel = direction.scale(Math.min(input.stepLength(), maximumStep) * commandStrength);
        double pendulumTime = Math.sqrt(Math.max(0.25D, Math.abs(limb.defaultFootPosition().y)) / 9.81D);
        Vec3 capture = horizontal(motion.localVelocity()).scale(pendulumTime * 0.65D);
        travel = travel.add(capture.scale(commandStrength));
        Vec3 turn = new Vec3(-limb.hipPosition().z, 0.0D, limb.hipPosition().x)
                .scale(input.yawDemand() * input.stepLength() * 0.5D);
        Vec3 nominal = limb.defaultFootPosition();
        double swing = Math.max(EPSILON, input.swingFraction());
        double stride;
        double lift = 0.0D;
        if (stance) {
            double stanceProgress = (phase - swing) / Math.max(EPSILON, 1.0D - swing);
            stride = 0.5D - Mth.clamp(stanceProgress, 0.0D, 1.0D);
        } else {
            double swingProgress = Mth.clamp(phase / swing, 0.0D, 1.0D);
            double easedSwing = swingProgress * swingProgress * (3.0D - 2.0D * swingProgress);
            stride = easedSwing - 0.5D;
            lift = Math.sin(Math.PI * swingProgress) * input.clearance();
        }
        return nominal.add(travel.add(turn).scale(stride))
                .subtract(horizontal(balance)).add(0.0D, lift, 0.0D);
    }

    // Get the vertical foot offset requested by the current crouch or jump pose
    private static Vec3 postureOffset(Input input, Limb limb, Posture posture) {
        double reach = limb.upperLength() + limb.lowerLength();
        double postureDepth = Math.min(reach * 0.24D, input.clearance() * 0.9D + 0.2D);
        double crouch = posture.crouchDemand() * postureDepth;
        double jump = posture.jumpDemand() * postureDepth;
        return new Vec3(0.0D, crouch + (posture.airborne() ? jump : -jump), 0.0D);
    }

    // Keep a load-bearing leg clear of its singular fully extended pose
    private static Vec3 minimumKneeBend(Limb limb, Vec3 foot) {
        Vec3 target = finite(foot);
        double horizontal = Math.sqrt(target.x * target.x + target.z * target.z);
        double radial = Math.max(0.0D, horizontal - limb.coxaLength());
        double vertical = -target.y;
        double reach = Math.sqrt(radial * radial + vertical * vertical);
        double lowerBound = Math.abs(limb.upperLength() - limb.lowerLength()) + EPSILON;
        double maximumReach = Mth.clamp((limb.upperLength() + limb.lowerLength()) * 0.86D,
                lowerBound, limb.upperLength() + limb.lowerLength() - EPSILON);
        if (reach <= maximumReach || reach <= EPSILON) return target;
        double scale = maximumReach / reach;
        if (horizontal <= EPSILON) return new Vec3(0.0D, -vertical * scale, 0.0D);
        double planar = limb.coxaLength() + radial * scale;
        return new Vec3(target.x * planar / horizontal, -vertical * scale,
                target.z * planar / horizontal);
    }

    // Get the organic counterbalance arm target
    private static Vec3 armTarget(Input input, Limb limb, Vec3 balance, double phase) {
        Vec3 command = horizontal(input.command());
        double swing = Math.sin(phase * Math.PI * 2.0D);
        Vec3 reaction = command.scale(-input.armSwing() * swing)
                .add(new Vec3(-limb.hipPosition().z, 0.0D, limb.hipPosition().x)
                        .scale(-input.yawDemand() * input.armSwing() * swing));
        return limb.defaultFootPosition().add(reaction).add(balance.scale(0.5D));
    }

    // Build one target and its inverse kinematics solution
    private static LimbTarget target(Limb limb, Vec3 foot, double phase, boolean stance) {
        return new LimbTarget(limb.id(), finite(foot), inverseKinematics(limb, foot), phase, stance);
    }

    // Get a corrective body offset toward the grounded support region
    private static Vec3 balanceOffset(Input input, List<Vec3> support, BodyMotion motion) {
        if (support.isEmpty()) return Vec3.ZERO;
        double height = Math.max(0.25D, support.stream()
                .mapToDouble(point -> Math.abs(point.y - input.bodyPosition().y)).average()
                .orElse(1.0D));
        double captureTime = Math.sqrt(height / 9.81D);
        Vec3 capture = input.bodyPosition().add(horizontal(motion.localVelocity()).scale(captureTime));
        Vec3 desired = closestSupportPoint(capture, support);
        Vec3 error = new Vec3(desired.x - input.bodyPosition().x, 0.0D,
                desired.z - input.bodyPosition().z);
        return error.scale(Mth.clamp(input.balanceGain(), 0.0D, 1.0D));
    }

    // Project the capture point into the current support region
    private static Vec3 closestSupportPoint(Vec3 point, List<Vec3> support) {
        List<Vec3> hull = supportHull(support);
        if (hull.isEmpty()) return Vec3.ZERO;
        if (hull.size() == 1) return hull.getFirst();
        if (hull.size() >= 3 && supportMargin(new Input(point, Vec3.ZERO, 0.0D, 0.0D,
                0.0D, 0.35D, 0.0D, 0.0D, 0.0D, 1.0D), hull) > EPSILON) {
            return point;
        }
        Vec3 closest = hull.getFirst();
        double nearest = Double.MAX_VALUE;
        int edges = hull.size() == 2 ? 1 : hull.size();
        for (int index = 0; index < edges; index++) {
            Vec3 first = hull.get(index);
            Vec3 second = hull.get((index + 1) % hull.size());
            Vec3 candidate = closestPointOnSegment(point, first, second);
            double distance = sqr(candidate.x - point.x) + sqr(candidate.z - point.z);
            if (distance < nearest) {
                closest = candidate;
                nearest = distance;
            }
        }
        return closest;
    }

    // Get the closest horizontal point on one support edge
    private static Vec3 closestPointOnSegment(Vec3 point, Vec3 first, Vec3 second) {
        double dx = second.x - first.x;
        double dz = second.z - first.z;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared <= EPSILON) return first;
        double fraction = Mth.clamp(((point.x - first.x) * dx + (point.z - first.z) * dz)
                / lengthSquared, 0.0D, 1.0D);
        return new Vec3(first.x + dx * fraction, point.y, first.z + dz * fraction);
    }

    // Get the minimum projected support distance from the body centre
    private static double supportMargin(Input input, List<Vec3> support) {
        if (support.size() < 2) return 0.0D;
        List<Vec3> hull = supportHull(support);
        if (hull.size() >= 3) {
            Vec3 center = input.bodyPosition();
            boolean counterClockwise = polygonArea(hull) >= 0.0D;
            double margin = Double.POSITIVE_INFINITY;
            for (int index = 0; index < hull.size(); index++) {
                Vec3 first = hull.get(index);
                Vec3 second = hull.get((index + 1) % hull.size());
                double side = cross2d(first, second, center);
                if (counterClockwise ? side < -EPSILON : side > EPSILON) return 0.0D;
                margin = Math.min(margin, pointSegmentDistance2d(center, first, second));
            }
            return Double.isFinite(margin) ? Math.max(0.0D, margin) : 0.0D;
        }
        double minimum = Double.POSITIVE_INFINITY;
        Vec3 center = input.bodyPosition();
        for (Vec3 point : support) {
            minimum = Math.min(minimum, Math.sqrt(
                    sqr(point.x - center.x) + sqr(point.z - center.z)));
        }
        return Double.isFinite(minimum) ? minimum : 0.0D;
    }

    // Get the centroid of a support polygon or its sparse-contact average
    private static Vec3 supportCentroid(List<Vec3> support) {
        List<Vec3> hull = supportHull(support);
        if (hull.size() < 3) {
            Vec3 average = Vec3.ZERO;
            for (Vec3 point : support) average = average.add(point);
            return average.scale(1.0D / support.size());
        }
        double area = polygonArea(hull);
        if (Math.abs(area) <= EPSILON) return hull.getFirst();
        double x = 0.0D;
        double z = 0.0D;
        for (int index = 0; index < hull.size(); index++) {
            Vec3 first = hull.get(index);
            Vec3 second = hull.get((index + 1) % hull.size());
            double cross = first.x * second.z - second.x * first.z;
            x += (first.x + second.x) * cross;
            z += (first.z + second.z) * cross;
        }
        return new Vec3(x / (6.0D * area), 0.0D, z / (6.0D * area));
    }

    // Build the planar convex support polygon in deterministic order
    private static List<Vec3> supportHull(List<Vec3> support) {
        List<Vec3> points = support.stream().map(ScmLeggedLocomotion::finite)
                .sorted(Comparator.comparingDouble((Vec3 point) -> point.x)
                        .thenComparingDouble(point -> point.z)).toList();
        if (points.size() < 3) return points;
        List<Vec3> lower = new ArrayList<>();
        for (Vec3 point : points) {
            while (lower.size() >= 2 && cross2d(lower.get(lower.size() - 2),
                    lower.getLast(), point) <= EPSILON) {
                lower.removeLast();
            }
            lower.add(point);
        }
        List<Vec3> upper = new ArrayList<>();
        for (int index = points.size() - 1; index >= 0; index--) {
            Vec3 point = points.get(index);
            while (upper.size() >= 2 && cross2d(upper.get(upper.size() - 2),
                    upper.getLast(), point) <= EPSILON) {
                upper.removeLast();
            }
            upper.add(point);
        }
        lower.removeLast();
        upper.removeLast();
        lower.addAll(upper);
        return List.copyOf(lower);
    }

    // Get the signed planar polygon area
    private static double polygonArea(List<Vec3> polygon) {
        double twiceArea = 0.0D;
        for (int index = 0; index < polygon.size(); index++) {
            Vec3 first = polygon.get(index);
            Vec3 second = polygon.get((index + 1) % polygon.size());
            twiceArea += first.x * second.z - second.x * first.z;
        }
        return twiceArea * 0.5D;
    }

    // Get one signed planar cross product
    private static double cross2d(Vec3 first, Vec3 second, Vec3 point) {
        return (second.x - first.x) * (point.z - first.z)
                - (second.z - first.z) * (point.x - first.x);
    }

    // Get the planar distance from a point to one support edge
    private static double pointSegmentDistance2d(Vec3 point, Vec3 first, Vec3 second) {
        double dx = second.x - first.x;
        double dz = second.z - first.z;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared <= EPSILON) {
            return Math.sqrt(sqr(point.x - first.x) + sqr(point.z - first.z));
        }
        double fraction = Mth.clamp(((point.x - first.x) * dx + (point.z - first.z) * dz)
                / lengthSquared, 0.0D, 1.0D);
        double x = first.x + dx * fraction;
        double z = first.z + dz * fraction;
        return Math.sqrt(sqr(point.x - x) + sqr(point.z - z));
    }

    // Solve one regularized three-by-three linear system
    private static Vec3 solveMatrix(double[][] matrix, Vec3 value) {
        double determinant = matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1])
                - matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
                + matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]);
        if (Math.abs(determinant) <= EPSILON) return Vec3.ZERO;
        double dx = value.x * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1])
                - matrix[0][1] * (value.y * matrix[2][2] - matrix[1][2] * value.z)
                + matrix[0][2] * (value.y * matrix[2][1] - matrix[1][1] * value.z);
        double dy = matrix[0][0] * (value.y * matrix[2][2] - matrix[1][2] * value.z)
                - value.x * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
                + matrix[0][2] * (matrix[1][0] * value.z - value.y * matrix[2][0]);
        double dz = matrix[0][0] * (matrix[1][1] * value.z - value.y * matrix[2][1])
                - matrix[0][1] * (matrix[1][0] * value.z - value.y * matrix[2][0])
                + value.x * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]);
        return new Vec3(dx / determinant, dy / determinant, dz / determinant);
    }

    // Get a horizontal finite vector
    private static Vec3 horizontal(Vec3 value) {
        Vec3 finite = finite(value);
        return new Vec3(finite.x, 0.0D, finite.z);
    }

    // Normalize one finite vector
    private static Vec3 finite(Vec3 value) {
        if (value == null || !Double.isFinite(value.x)
                || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            return Vec3.ZERO;
        }
        return value;
    }

    // Normalize one finite scalar
    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }

    // Wrap a gait phase into zero through one
    private static double wrap(double value) {
        double wrapped = finite(value) % 1.0D;
        return wrapped < 0.0D ? wrapped + 1.0D : wrapped;
    }

    // Square one scalar
    private static double sqr(double value) {
        return value * value;
    }

    // Define a leg or counterbalance arm
    public record Limb(
            String id, LimbKind kind, Vec3 hipPosition, Vec3 defaultFootPosition,
            double coxaLength, double upperLength, double lowerLength,
            double phaseOffset, double yawOffset, double hipOffset, double kneeOffset
    ) {
        // Normalize the limb geometry
        public Limb {
            id = id == null ? "" : id.strip();
            kind = kind == null ? LimbKind.LEG : kind;
            hipPosition = finite(hipPosition);
            defaultFootPosition = finite(defaultFootPosition);
            coxaLength = Math.max(0.0D, finite(coxaLength));
            upperLength = Math.max(EPSILON, finite(upperLength));
            lowerLength = Math.max(EPSILON, finite(lowerLength));
            phaseOffset = wrap(phaseOffset);
            yawOffset = finite(yawOffset);
            hipOffset = finite(hipOffset);
            kneeOffset = finite(kneeOffset);
        }

        // Check whether this has sufficient geometry for inverse kinematics
        public boolean valid() {
            return !id.isBlank() && upperLength > EPSILON && lowerLength > EPSILON;
        }
    }

    // Define one live revolute joint used by redundant inverse kinematics
    public record ArticulatedJoint(Vec3 position, Vec3 axis, double weight, double maximumDelta) {
        // Normalize the joint geometry
        public ArticulatedJoint {
            position = finite(position);
            axis = finite(axis);
            axis = axis.lengthSqr() <= EPSILON ? Vec3.ZERO : axis.normalize();
            weight = Math.max(EPSILON, finite(weight));
            maximumDelta = Math.max(EPSILON, finite(maximumDelta));
        }

        // Check whether the joint has a usable axis
        public boolean valid() {
            return axis.lengthSqr() > EPSILON * EPSILON;
        }
    }

    // Store one damped least-squares joint correction
    public record RedundantIkSolution(List<Double> jointDeltas, Vec3 residual) {
        // Normalize the numerical result
        public RedundantIkSolution {
            jointDeltas = jointDeltas == null ? List.of() : jointDeltas.stream()
                    .map(ScmLeggedLocomotion::finite).toList();
            residual = finite(residual);
        }
    }

    // Define one limb's role
    public enum LimbKind {
        LEG,
        ARM
    }

    // Define the body and input state for one solver update
    public record Input(
            Vec3 bodyPosition, Vec3 command, double yawDemand, double jumpDemand,
            double phase, double swingFraction, double stepLength, double clearance,
            double armSwing, double balanceGain
    ) {
        // Normalize the locomotion request
        public Input {
            bodyPosition = finite(bodyPosition);
            command = finite(command);
            yawDemand = Mth.clamp(finite(yawDemand), -1.0D, 1.0D);
            jumpDemand = Mth.clamp(finite(jumpDemand), 0.0D, 1.0D);
            phase = wrap(phase);
            swingFraction = Mth.clamp(finite(swingFraction), 0.1D, 0.9D);
            stepLength = Math.max(0.0D, finite(stepLength));
            clearance = Math.max(0.0D, finite(clearance));
            armSwing = Math.max(0.0D, finite(armSwing));
            balanceGain = Mth.clamp(finite(balanceGain), 0.0D, 1.0D);
        }

        // Create a stationary default request
        public static Input idle() {
            return new Input(Vec3.ZERO, Vec3.ZERO, 0.0D, 0.0D,
                    0.0D, 0.35D, 1.0D, 0.35D, 0.35D, 0.35D);
        }
    }

    // Define the body posture independent of horizontal gait motion
    public record Posture(double crouchDemand, double jumpDemand, boolean airborne) {
        public static final Posture NONE = new Posture(0.0D, 0.0D, false);

        // Normalize the posture request
        public Posture {
            crouchDemand = Mth.clamp(finite(crouchDemand), 0.0D, 1.0D);
            jumpDemand = Mth.clamp(finite(jumpDemand), 0.0D, 1.0D);
        }
    }

    // Define the body-local movement observed during one locomotion update
    public record BodyMotion(Vec3 localVelocity, double deltaSeconds) {
        public static final BodyMotion NONE = new BodyMotion(Vec3.ZERO, 0.0D);

        // Normalize the body movement
        public BodyMotion {
            localVelocity = finite(localVelocity);
            deltaSeconds = Mth.clamp(finite(deltaSeconds), 0.0D, 0.25D);
        }

        // Get the bounded local body translation for this update
        private Vec3 translation() {
            Vec3 translation = localVelocity.scale(deltaSeconds);
            return translation.lengthSqr() > 0.4225D ? translation.normalize().scale(0.65D)
                    : translation;
        }
    }

    // Retain planted foot targets while the body moves over the ground
    public static final class GaitState {
        private final Map<String, StanceFoot> stanceFeet = new LinkedHashMap<>();
        private double phase;

        // Clear every retained foot target
        public void clear() {
            stanceFeet.clear();
            phase = 0.0D;
        }

        // Advance a retained gait phase without resetting it when movement pauses
        public double advancePhase(double movementDemand, double deltaSeconds, double cyclesPerSecond) {
            double demand = Mth.clamp(Math.abs(finite(movementDemand)), 0.0D, 1.0D);
            double seconds = Mth.clamp(finite(deltaSeconds), 0.0D, 0.25D);
            double rate = Mth.clamp(finite(cyclesPerSecond), 0.0D, 12.0D);
            if (demand <= EPSILON || seconds <= EPSILON || rate <= EPSILON) return phase;
            phase = wrap(phase + demand * seconds * rate);
            return phase;
        }

        // Resolve one foot target from its stance history and the observed body motion
        private Vec3 resolveFoot(
                Limb limb, Vec3 requested, boolean stance, Contact contact, BodyMotion motion
        ) {
            if (limb == null || !stance) {
                if (limb != null) stanceFeet.remove(limb.id());
                return requested;
            }
            StanceFoot retained = stanceFeet.get(limb.id());
            Vec3 target = retained == null ? requested : retained.position().subtract(motion.translation());
            if (contact != null && contact.grounded()) {
                target = new Vec3(target.x, contact.footPosition().y, target.z);
            }
            stanceFeet.put(limb.id(), new StanceFoot(target));
            return target;
        }

        // Remove stance feet for limbs which are no longer part of this body
        private void retain(Collection<Limb> limbs) {
            Set<String> ids = new HashSet<>();
            if (limbs != null) {
                limbs.stream().filter(Objects::nonNull).map(Limb::id).forEach(ids::add);
            }
            stanceFeet.keySet().removeIf(id -> !ids.contains(id));
        }

        // Store one retained body-local foot target
        private record StanceFoot(Vec3 position) {
            private StanceFoot {
                position = finite(position);
            }
        }
    }

    // Store a measured contact point for one foot
    public record Contact(String limbId, boolean grounded, Vec3 position, Vec3 footPosition) {
        // Initialize one contact with a shared support and limb-frame position
        public Contact(String limbId, boolean grounded, Vec3 position) {
            this(limbId, grounded, position, position);
        }

        // Normalize the contact point
        public Contact {
            limbId = limbId == null ? "" : limbId.strip();
            position = finite(position);
            footPosition = finite(footPosition);
        }
    }

    // Store an analytical three-joint target
    public record JointAngles(double yaw, double hip, double knee, double reach) {
        public static final JointAngles ZERO = new JointAngles(0.0D, 0.0D, 0.0D, 0.0D);

        // Normalize the angle solution
        public JointAngles {
            yaw = finite(yaw);
            hip = finite(hip);
            knee = finite(knee);
            reach = Math.max(0.0D, finite(reach));
        }
    }

    // Store one limb's requested pose
    public record LimbTarget(
            String limbId, Vec3 footPosition, JointAngles angles, double phase, boolean stance
    ) {
        // Normalize the limb target
        public LimbTarget {
            limbId = limbId == null ? "" : limbId.strip();
            footPosition = finite(footPosition);
            angles = angles == null ? JointAngles.ZERO : angles;
            phase = wrap(phase);
        }
    }

    // Store the complete solver output
    public record Plan(
            Map<String, LimbTarget> targets, Vec3 balanceOffset,
            int supportCount, double supportMargin
    ) {
        // Normalize the planner output
        public Plan {
            targets = targets == null ? Map.of() : Map.copyOf(targets);
            balanceOffset = finite(balanceOffset);
            supportCount = Math.max(0, supportCount);
            supportMargin = Math.max(0.0D, finite(supportMargin));
        }
    }
}
