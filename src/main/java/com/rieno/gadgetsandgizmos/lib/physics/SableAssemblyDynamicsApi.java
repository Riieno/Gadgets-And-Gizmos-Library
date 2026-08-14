package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Sample loaded Sable body mass and inertia in one shared root-local frame
public final class SableAssemblyDynamicsApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable assembly dynamics API
    private SableAssemblyDynamicsApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sample the sable assembly dynamics API
    public static Snapshot sample(@Nullable ServerLevel level, @Nullable UUID rootSubLevelId) {
        if (level == null || rootSubLevelId == null) {
            return Snapshot.unavailable(rootSubLevelId);
        }
        try {
            Object resolved = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                    .getContainer(level).getSubLevel(rootSubLevelId);
            return resolved instanceof ServerSubLevel root ? sample(root) : Snapshot.unavailable(rootSubLevelId);
        } catch (RuntimeException | LinkageError err) {
            return Snapshot.unavailable(rootSubLevelId);
        }
    }

    // Sample the sable assembly dynamics API
    public static Snapshot sample(@Nullable ServerSubLevel root) {
        UUID rootId = id(root);
        if (root == null || rootId == null || root.isRemoved()) {
            return Snapshot.unavailable(rootId);
        }
        try {
            Set<ServerSubLevel> bodies = new LinkedHashSet<>();
            bodies.add(root);
            for (SubLevel connected : SubLevelHelper.getConnectedChain(root)) {
                if (connected instanceof ServerSubLevel body && !body.isRemoved()
                        && body.getLevel() == root.getLevel()) {
                    bodies.add(body);
                }
            }

            List<BodyInput> inputs = bodies.stream()
                    .map(body -> new BodyInput(body, body == root ? 0 : -1,
                            -1, rootId))
                    .sorted(java.util.Comparator.comparingInt(BodyInput::depth).reversed()
                            .thenComparing(input -> input.subLevel().getUniqueId().toString()))
                    .toList();
            return sample(root, inputs);
        } catch (RuntimeException | LinkageError err) {
            return Snapshot.unavailable(rootId);
        }
    }

    // Sample only the loaded bodies in a topology snapshot
    public static Snapshot sample(@Nullable SableAssemblyTopologyApi.Topology topology) {
        UUID rootId = topology == null ? null : topology.rootSubLevelId();
        if (topology == null || !topology.available() || rootId == null) {
            return Snapshot.unavailable(rootId);
        }
        Optional<SableAssemblyTopologyApi.Body> rootBody = topology.body(rootId);
        if (rootBody.isEmpty()) {
            return Snapshot.unavailable(rootId);
        }
        try {
            List<BodyInput> bodies = topology.bodies().stream()
                    .map(body -> new BodyInput(body.subLevel(), body.depth(),
                            body.carriageDepth(), body.carriageRootSubLevelId()))
                    .toList();
            return sample(rootBody.get().subLevel(), bodies);
        } catch (RuntimeException | LinkageError err) {
            return Snapshot.unavailable(rootId);
        }
    }

    // Rebuild the dynamics snapshot for only the requested bodies
    public static Snapshot aggregate(@Nullable Snapshot source,
                                     @Nullable Collection<UUID> selectedSubLevelIds) {
        UUID rootId = source == null ? null : source.rootSubLevelId();
        if (source == null || !source.loaded() || selectedSubLevelIds == null) {
            return Snapshot.unavailable(rootId);
        }

        LinkedHashSet<UUID> selectedIds = new LinkedHashSet<>();
        boolean complete = true;
        for (UUID subLevelId : selectedSubLevelIds) {
            if (subLevelId == null) {
                complete = false;
            } else {
                selectedIds.add(subLevelId);
            }
        }
        List<BodyDynamics> selectedBodies = source.bodies().stream()
                .filter(body -> selectedIds.contains(body.subLevelId()))
                .toList();
        complete &= selectedBodies.size() == selectedIds.size()
                && selectedBodies.stream().allMatch(BodyDynamics::massAvailable);
        return aggregate(rootId, source.physicsAvailable(), selectedBodies, complete);
    }

    // Combine the sable assembly dynamics API
    private static Snapshot aggregate(@Nullable UUID rootId,
                                      boolean physicsAvailable,
                                      List<BodyDynamics> bodies,
                                      boolean complete) {
        if (!complete) {
            return new Snapshot(rootId, true, physicsAvailable, 0.0D,
                    Vec3.ZERO, Tensor.ZERO, Tensor.ZERO, bodies);
        }
        if (bodies.isEmpty()) {
            return new Snapshot(rootId, true, physicsAvailable, 0.0D,
                    Vec3.ZERO, Tensor.ZERO, Tensor.ZERO, List.of());
        }

        double totalMass = 0.0D;
        Vec3 weightedCenter = Vec3.ZERO;
        for (BodyDynamics body : bodies) {
            if (body == null || !body.massAvailable()
                    || !finite(body.centerOfMass())) {
                return new Snapshot(rootId, true, physicsAvailable, 0.0D,
                        Vec3.ZERO, Tensor.ZERO, Tensor.ZERO, bodies);
            }
            weightedCenter = weightedCenter.add(
                    body.centerOfMass().scale(body.mass()));
            totalMass += body.mass();
            if (!finite(weightedCenter) || !Double.isFinite(totalMass)) {
                return new Snapshot(rootId, true, physicsAvailable, 0.0D,
                        Vec3.ZERO, Tensor.ZERO, Tensor.ZERO, bodies);
            }
        }
        if (totalMass <= 1.0E-9D) {
            return new Snapshot(rootId, true, physicsAvailable, 0.0D,
                    Vec3.ZERO, Tensor.ZERO, Tensor.ZERO, bodies);
        }

        Vec3 centerOfMass = weightedCenter.scale(1.0D / totalMass);
        if (!finite(centerOfMass)) {
            return new Snapshot(rootId, true, physicsAvailable, 0.0D,
                    Vec3.ZERO, Tensor.ZERO, Tensor.ZERO, bodies);
        }
        Tensor inertia = Tensor.ZERO;
        for (BodyDynamics body : bodies) {
            inertia = inertia.add(body.inertia().shifted(
                    body.centerOfMass().subtract(centerOfMass), body.mass()));
        }
        return new Snapshot(rootId, true, physicsAvailable, totalMass, centerOfMass,
                inertia, inertia.inverse(), bodies);
    }

    // Sample the sable assembly dynamics API
    private static Snapshot sample(ServerSubLevel root, Collection<BodyInput> bodies) {
        UUID rootId = root.getUniqueId();
        try {
            double totalMass = 0.0D;
            Vec3 weightedCenter = Vec3.ZERO;
            List<SampledBody> validBodies = new ArrayList<>();
            List<BodyDynamics> bodyDynamics = new ArrayList<>();
            boolean aggregateComplete = true;
            for (BodyInput input : bodies) {
                ServerSubLevel body = input.subLevel();
                if (body == null || body.isRemoved() || body.getLevel() != root.getLevel()) {
                    continue;
                }
                MassData massData = body.getMassTracker();
                double mass = massData == null ? 0.0D : massData.getMass();
                Vector3dc center = massData == null ? null : massData.getCenterOfMass();
                boolean physicsAvailable = physicsAvailable(body);
                if (!Double.isFinite(mass) || mass <= 0.0D || !finite(center)) {
                    aggregateComplete &= Double.isFinite(mass) && mass == 0.0D;
                    bodyDynamics.add(BodyDynamics.unavailable(body.getUniqueId(),
                            input.depth(), input.carriageDepth(),
                            input.carriageRootSubLevelId(), physicsAvailable));
                    continue;
                }
                Vec3 rootCenter = positionInRoot(root, body, center);
                if (!finite(rootCenter)) {
                    aggregateComplete = false;
                    bodyDynamics.add(BodyDynamics.unavailable(body.getUniqueId(),
                            input.depth(), input.carriageDepth(),
                            input.carriageRootSubLevelId(), physicsAvailable));
                    continue;
                }
                Tensor bodyInertia = tensorInRoot(root, body, massData.getInertiaTensor());
                if (bodyInertia == null) {
                    bodyInertia = Tensor.ZERO;
                }
                validBodies.add(new SampledBody(mass, rootCenter, bodyInertia));
                bodyDynamics.add(new BodyDynamics(body.getUniqueId(), input.depth(),
                        input.carriageDepth(), input.carriageRootSubLevelId(),
                        physicsAvailable, mass, rootCenter, bodyInertia, bodyInertia.inverse()));
                weightedCenter = weightedCenter.add(rootCenter.scale(mass));
                totalMass += mass;
            }
            boolean physicsAvailable = physicsAvailable(root);
            if (!aggregateComplete || !Double.isFinite(totalMass) || totalMass <= 1.0E-9D) {
                return new Snapshot(rootId, true, physicsAvailable, 0.0D,
                        Vec3.ZERO, Tensor.ZERO, Tensor.ZERO, bodyDynamics);
            }

            Vec3 centerOfMass = weightedCenter.scale(1.0D / totalMass);
            Tensor inertia = Tensor.ZERO;
            for (SampledBody body : validBodies) {
                inertia = inertia.add(body.inertia().shifted(body.center().subtract(centerOfMass), body.mass()));
            }
            Tensor inverse = inertia.inverse();
            return new Snapshot(rootId, true, physicsAvailable, totalMass, centerOfMass,
                    inertia, inverse, bodyDynamics);
        } catch (RuntimeException | LinkageError err) {
            return Snapshot.unavailable(rootId);
        }
    }

    // Check if the physics is available
    private static boolean physicsAvailable(ServerSubLevel body) {
        try {
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            return handle != null && handle.isValid();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Get the id
    private static @Nullable UUID id(@Nullable ServerSubLevel body) {
        try {
            return body == null ? null : body.getUniqueId();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    // Get the position in root
    private static Vec3 positionInRoot(ServerSubLevel root, ServerSubLevel src, Vector3dc localPosition) {
        Vector3d pos = new Vector3d(localPosition.x(), localPosition.y(), localPosition.z());
        src.logicalPose().transformPosition(pos);
        root.logicalPose().transformPositionInverse(pos);
        return new Vec3(pos.x, pos.y, pos.z);
    }

    // Get the tensor in root
    private static @Nullable Tensor tensorInRoot(ServerSubLevel root, ServerSubLevel src,
                                                 @Nullable Matrix3dc local) {
        if (!validInertia(local)) {
            return null;
        }
        Vec3[] axes = new Vec3[3];
        for (int axis = 0; axis < axes.length; axis++) {
            Vector3d dir = switch (axis) {
                case 0 -> new Vector3d(1.0D, 0.0D, 0.0D);
                case 1 -> new Vector3d(0.0D, 1.0D, 0.0D);
                default -> new Vector3d(0.0D, 0.0D, 1.0D);
            };
            src.logicalPose().orientation().transform(dir);
            root.logicalPose().orientation().transformInverse(dir);
            axes[axis] = new Vec3(dir.x, dir.y, dir.z);
            if (!finite(axes[axis])) {
                return null;
            }
        }
        double[][] rotation = {
                {axes[0].x, axes[1].x, axes[2].x},
                {axes[0].y, axes[1].y, axes[2].y},
                {axes[0].z, axes[1].z, axes[2].z}
        };
        Tensor sourceTensor = Tensor.of(local);
        double[][] rotated = new double[3][3];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                for (int left = 0; left < 3; left++) {
                    for (int right = 0; right < 3; right++) {
                        rotated[row][column] += rotation[row][left]
                                * sourceTensor.value(left, right) * rotation[column][right];
                    }
                }
            }
        }
        return finite(rotated) ? Tensor.of(rotated) : null;
    }

    // Check if the inertia is valid
    private static boolean validInertia(@Nullable Matrix3dc matrix) {
        if (matrix == null) {
            return false;
        }
        double[] values = {
                matrix.m00(), matrix.m01(), matrix.m02(),
                matrix.m10(), matrix.m11(), matrix.m12(),
                matrix.m20(), matrix.m21(), matrix.m22()
        };
        double scale = 1.0D;
        for (double val : values) {
            if (!Double.isFinite(val)) {
                return false;
            }
            scale = Math.max(scale, Math.abs(val));
        }
        double tolerance = 1.0E-8D * scale;
        if (matrix.m00() < -tolerance || matrix.m11() < -tolerance
                || matrix.m22() < -tolerance
                || Math.abs(matrix.m01() - matrix.m10()) > tolerance
                || Math.abs(matrix.m02() - matrix.m20()) > tolerance
                || Math.abs(matrix.m12() - matrix.m21()) > tolerance) {
            return false;
        }
        double xy = (matrix.m01() + matrix.m10()) * 0.5D;
        double xz = (matrix.m02() + matrix.m20()) * 0.5D;
        double yz = (matrix.m12() + matrix.m21()) * 0.5D;
        double squaredTolerance = 1.0E-8D * scale * scale;
        if (matrix.m00() * matrix.m11() - xy * xy < -squaredTolerance
                || matrix.m00() * matrix.m22() - xz * xz < -squaredTolerance
                || matrix.m11() * matrix.m22() - yz * yz < -squaredTolerance) {
            return false;
        }
        double determinant = matrix.m00() * (matrix.m11() * matrix.m22() - yz * yz)
                - xy * (xy * matrix.m22() - yz * xz)
                + xz * (xy * yz - matrix.m11() * xz);
        return determinant >= -1.0E-8D * scale * scale * scale;
    }

    // Normalize the value to a finite result
    private static boolean finite(double[][] values) {
        for (double[] row : values) {
            for (double val : row) {
                if (!Double.isFinite(val)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Normalize the value to a finite result
    private static boolean finite(@Nullable Vector3dc val) {
        return val != null && Double.isFinite(val.x())
                && Double.isFinite(val.y()) && Double.isFinite(val.z());
    }

    // Normalize the value to a finite result
    private static boolean finite(@Nullable Vec3 val) {
        return val != null && Double.isFinite(val.x)
                && Double.isFinite(val.y) && Double.isFinite(val.z);
    }

    // Store the body input
    private record BodyInput(ServerSubLevel subLevel, int depth, int carriageDepth,
                             UUID carriageRootSubLevelId) {
    }

    // Store the sampled body
    private record SampledBody(double mass, Vec3 center, Tensor inertia) {
    }

    // Store the snapshot
    public record Snapshot(@Nullable UUID rootSubLevelId, boolean loaded, boolean physicsAvailable,
                           double mass, Vec3 centerOfMass, Tensor inertia, Tensor inverseInertia,
                           List<BodyDynamics> bodies) {
        // Initialize the snapshot
        public Snapshot {
            mass = Double.isFinite(mass) && mass > 0.0D ? mass : 0.0D;
            centerOfMass = finite(centerOfMass) ? centerOfMass : Vec3.ZERO;
            inertia = inertia == null ? Tensor.ZERO : inertia;
            inverseInertia = inverseInertia == null ? Tensor.ZERO : inverseInertia;
            bodies = bodies == null ? List.of() : List.copyOf(bodies);
        }

        // Initialize the snapshot
        public Snapshot(@Nullable UUID rootSubLevelId, boolean loaded,
                        boolean physicsAvailable, double mass, Vec3 centerOfMass,
                        Tensor inertia, Tensor inverseInertia) {
            this(rootSubLevelId, loaded, physicsAvailable, mass, centerOfMass,
                    inertia, inverseInertia, List.of());
        }

        // Check if the mass is available
        public boolean massAvailable() {
            return mass > 0.0D;
        }

        // Get the body
        public Optional<BodyDynamics> body(@Nullable UUID subLevelId) {
            return subLevelId == null ? Optional.empty() : bodies.stream()
                    .filter(body -> subLevelId.equals(body.subLevelId())).findFirst();
        }

        // Rebuild exactly the requested bodies without changing their root-local frame
        public Snapshot aggregate(@Nullable Collection<UUID> selectedSubLevelIds) {
            return SableAssemblyDynamicsApi.aggregate(this, selectedSubLevelIds);
        }

        // Create an unavailable snapshot
        private static Snapshot unavailable(@Nullable UUID rootSubLevelId) {
            return new Snapshot(rootSubLevelId, false, false, 0.0D,
                    Vec3.ZERO, Tensor.ZERO, Tensor.ZERO, List.of());
        }
    }

    // Store each body's values in the sampled root's local frame
    public record BodyDynamics(UUID subLevelId, int depth, int carriageDepth,
                               UUID carriageRootSubLevelId, boolean physicsAvailable,
                               double mass, Vec3 centerOfMass, Tensor inertia,
                               Tensor inverseInertia) {
        // Initialize the body dynamics
        public BodyDynamics {
            depth = Math.max(-1, depth);
            carriageDepth = Math.max(-1, carriageDepth);
            carriageRootSubLevelId = carriageRootSubLevelId == null
                    ? subLevelId : carriageRootSubLevelId;
            mass = Double.isFinite(mass) && mass > 0.0D ? mass : 0.0D;
            centerOfMass = finite(centerOfMass) ? centerOfMass : Vec3.ZERO;
            inertia = inertia == null ? Tensor.ZERO : inertia;
            inverseInertia = inverseInertia == null ? Tensor.ZERO : inverseInertia;
        }

        // Check if the mass is available
        public boolean massAvailable() {
            return mass > 0.0D;
        }

        // Create an unavailable body dynamics
        private static BodyDynamics unavailable(UUID subLevelId, int depth,
                                                int carriageDepth,
                                                UUID carriageRootSubLevelId,
                                                boolean physicsAvailable) {
            return new BodyDynamics(subLevelId, depth, carriageDepth,
                    carriageRootSubLevelId, physicsAvailable, 0.0D, Vec3.ZERO,
                    Tensor.ZERO, Tensor.ZERO);
        }
    }

    // Store one finite 3×3 inertia tensor in root-local coordinates
    public record Tensor(double m00, double m01, double m02,
                         double m10, double m11, double m12,
                         double m20, double m21, double m22) {
        public static final Tensor ZERO = new Tensor(0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

        // Initialize the tensor
        public Tensor {
            if (!Double.isFinite(m00) || !Double.isFinite(m01) || !Double.isFinite(m02)
                    || !Double.isFinite(m10) || !Double.isFinite(m11)
                    || !Double.isFinite(m12) || !Double.isFinite(m20)
                    || !Double.isFinite(m21) || !Double.isFinite(m22)) {
                m00 = 0.0D; m01 = 0.0D; m02 = 0.0D;
                m10 = 0.0D; m11 = 0.0D; m12 = 0.0D;
                m20 = 0.0D; m21 = 0.0D; m22 = 0.0D;
            }
        }

        // Create the tensor
        public static Tensor of(Matrix3dc matrix) {
            return !validInertia(matrix) ? ZERO
                    : new Tensor(matrix.m00(), matrix.m01(), matrix.m02(),
                    matrix.m10(), matrix.m11(), matrix.m12(),
                    matrix.m20(), matrix.m21(), matrix.m22());
        }

        // Create the tensor
        private static Tensor of(double[][] values) {
            return new Tensor(values[0][0], values[0][1], values[0][2],
                    values[1][0], values[1][1], values[1][2],
                    values[2][0], values[2][1], values[2][2]);
        }

        // Transform the tensor
        public Vec3 transform(Vec3 vector) {
            if (!SableAssemblyDynamicsApi.finite(vector)) {
                return Vec3.ZERO;
            }
            return new Vec3(m00 * vector.x + m01 * vector.y + m02 * vector.z,
                    m10 * vector.x + m11 * vector.y + m12 * vector.z,
                    m20 * vector.x + m21 * vector.y + m22 * vector.z);
        }

        // Get the maximum imum diagonal
        public double maximumDiagonal() {
            return Math.max(0.0D, Math.max(m00, Math.max(m11, m22)));
        }

        // Add the tensor
        private Tensor add(Tensor other) {
            return new Tensor(m00 + other.m00, m01 + other.m01, m02 + other.m02,
                    m10 + other.m10, m11 + other.m11, m12 + other.m12,
                    m20 + other.m20, m21 + other.m21, m22 + other.m22);
        }

        // Get the shifted
        private Tensor shifted(Vec3 offset, double mass) {
            if (!SableAssemblyDynamicsApi.finite(offset)
                    || !Double.isFinite(mass) || mass <= 0.0D) {
                return this;
            }
            double x = offset.x;
            double y = offset.y;
            double z = offset.z;
            double squared = x * x + y * y + z * z;
            return add(new Tensor(mass * (squared - x * x), -mass * x * y, -mass * x * z,
                    -mass * y * x, mass * (squared - y * y), -mass * y * z,
                    -mass * z * x, -mass * z * y, mass * (squared - z * z)));
        }

        // Get the inverse
        private Tensor inverse() {
            double c00 = m11 * m22 - m12 * m21;
            double c01 = m02 * m21 - m01 * m22;
            double c02 = m01 * m12 - m02 * m11;
            double c10 = m12 * m20 - m10 * m22;
            double c11 = m00 * m22 - m02 * m20;
            double c12 = m02 * m10 - m00 * m12;
            double c20 = m10 * m21 - m11 * m20;
            double c21 = m01 * m20 - m00 * m21;
            double c22 = m00 * m11 - m01 * m10;
            double determinant = m00 * c00 + m01 * c10 + m02 * c20;
            if (!Double.isFinite(determinant) || Math.abs(determinant) <= 1.0E-12D) {
                return ZERO;
            }
            double inverse = 1.0D / determinant;
            return new Tensor(c00 * inverse, c01 * inverse, c02 * inverse,
                    c10 * inverse, c11 * inverse, c12 * inverse,
                    c20 * inverse, c21 * inverse, c22 * inverse);
        }

        // Get the value
        private double value(int row, int column) {
            return switch (row * 3 + column) {
                case 0 -> m00; case 1 -> m01; case 2 -> m02;
                case 3 -> m10; case 4 -> m11; case 5 -> m12;
                case 6 -> m20; case 7 -> m21; default -> m22;
            };
        }

    }
}
