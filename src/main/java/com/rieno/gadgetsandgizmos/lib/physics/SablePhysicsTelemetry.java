package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Share stable body telemetry with diagrams, graphs and other physics consumers
public final class SablePhysicsTelemetry{

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ServerSubLevel, CachedSnapshot> SNAPSHOT_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Keep telemetry access on the static API
    private SablePhysicsTelemetry(){}


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sample mass, pose and forces without requiring a block entity at the anchor
    public static Snapshot sample(Level level, @Nullable UUID subLevelId){
        if(level == null || subLevelId == null){
            return Snapshot.unavailable(subLevelId);
        }
        Object resolved = SubLevelBlockEntityCollector.ensureSubLevelLoaded(level, subLevelId);
        if(!(resolved instanceof ServerSubLevel subLevel) || subLevel.isRemoved()){
            return Snapshot.unavailable(subLevelId);
        }

        long gameTime = subLevel.getLevel().getGameTime();
        CachedSnapshot cached = SNAPSHOT_CACHE.get(subLevel);
        if(cached != null && cached.gameTime() == gameTime){
            return cached.snapshot();
        }

        Snapshot collected;
        try{
            collected = collect(subLevel);
        }catch(RuntimeException ignored){
            collected = Snapshot.unavailable(subLevelId);
        }
        SNAPSHOT_CACHE.put(subLevel, new CachedSnapshot(gameTime, collected));
        return collected;
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Collect body telemetry while keeping optional physics reads independent
    private static Snapshot collect(ServerSubLevel subLevel){
        subLevel.enableIndividualQueuedForcesTracking(true);
        MassData massData = subLevel.getMassTracker();
        double mass = massData == null ? 0.0D : finite(massData.getMass());
        double inverseMass = massData == null ? 0.0D : finite(massData.getInverseMass());
        Matrix3d inertiaTensor = copy(massData == null ? null : massData.getInertiaTensor());
        Vector3d centerOfMass = copy(massData == null ? null : massData.getCenterOfMass());
        Pose3dc pose = subLevel.logicalPose();
        Vector3d pos = copy(pose == null ? null : pose.position());
        Quaterniond orientation = copy(pose == null ? null : pose.orientation());
        Vector3d worldCenterOfMass = pose == null
                ? new Vector3d(centerOfMass)
                : pose.transformPosition(centerOfMass, new Vector3d());

        Vector3d linearVelocity = new Vector3d();
        Vector3d angularVelocity = new Vector3d();
        try{
            RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            if(handle != null && handle.isValid()){
                handle.getLinearVelocity(linearVelocity);
                handle.getAngularVelocity(angularVelocity);
            }
        }catch(RuntimeException | LinkageError err){
            linearVelocity.zero(); angularVelocity.zero();
        }

        List<ForceSample> forces;
        try{ forces = diagramForces(subLevel, pose); }
        catch(RuntimeException | LinkageError err){ forces = List.of(); }
        Vector3d totalForce = new Vector3d();
        Vector3d totalTorque = new Vector3d();
        for(ForceSample force : forces){
            totalForce.add(force.force());
            Vector3d torque = new Vector3d(force.point())
                    .sub(centerOfMass)
                    .cross(force.force());
            totalTorque.add(torque);
        }
        Vector3d worldTotalForce = pose == null
                ? new Vector3d(totalForce)
                : pose.transformNormal(totalForce, new Vector3d());
        Vector3d worldTotalTorque = pose == null
                ? new Vector3d(totalTorque)
                : pose.transformNormal(totalTorque, new Vector3d());

        String name = subLevel.getName();
        return new Snapshot(
                true,
                subLevel.getUniqueId(),
                name == null ? "" : name,
                mass,
                inverseMass,
                inertiaTensor,
                centerOfMass,
                worldCenterOfMass,
                pos,
                orientation,
                linearVelocity,
                angularVelocity,
                totalForce,
                worldTotalForce,
                totalTorque,
                worldTotalTorque,
                List.copyOf(forces));
    }

    // Convert recorded impulses and gravity into body-local forces
    private static List<ForceSample> diagramForces(ServerSubLevel subLevel, @Nullable Pose3dc pose){
        Map<ForceGroup, List<QueuedForceGroup.PointForce>> recorded = new LinkedHashMap<>();
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(subLevel.getLevel());
        double step = system == null ? 0.05D : 0.05D / Math.max(1, system.getConfig().substepsPerTick);
        Map<ForceGroup, QueuedForceGroup> queued = subLevel.getQueuedForceGroups();
        if(queued != null) queued.forEach((group, forces) -> {
            if(group != null && forces != null && forces.getRecordedPointForces() != null)
                recorded.put(group, new ArrayList<>(forces.getRecordedPointForces()));
        });
        List<ForceSample> samples = new ArrayList<>(convertForces(recorded, pose, 1.0D / step));
        var mass = subLevel.getMassTracker();
        ForceGroup gravity = ForceGroups.GRAVITY.get();
        if(mass != null && pose != null && gravity != null){
            Vector3d point = copy(mass.getCenterOfMass());
            Vector3d force = pose.transformNormalInverse(
                    dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(subLevel.getLevel()), new Vector3d())
                    .mul(finite(mass.getMass()));
            ResourceLocation id = ForceGroups.REGISTRY.getKey(gravity);
            samples.add(new ForceSample(id == null ? "" : id.toString(), gravity.name().getString(), gravity.color(),
                    point, force, pose.transformPosition(point, new Vector3d()), pose.transformNormal(force, new Vector3d())));
        }
        return List.copyOf(samples);
    }

    // Copy recorded forces into stable local and world frames
    private static List<ForceSample> convertForces(
            Map<ForceGroup, List<QueuedForceGroup.PointForce>> groupedForces,
            @Nullable Pose3dc pose,
            double scale){
        if(groupedForces == null || groupedForces.isEmpty()){
            return List.of();
        }
        List<ForceSample> samples = new ArrayList<>();
        for(Map.Entry<ForceGroup, List<QueuedForceGroup.PointForce>> entry : groupedForces.entrySet()){
            ForceGroup group = entry.getKey();
            if(group == null || entry.getValue() == null){
                continue;
            }
            ResourceLocation groupKey = ForceGroups.REGISTRY.getKey(group);
            String groupId = groupKey == null ? "" : groupKey.toString();
            String groupName = group.name() == null ? groupId : group.name().getString();
            for(QueuedForceGroup.PointForce pointForce : entry.getValue()){
                if(pointForce == null || pointForce.point() == null || pointForce.force() == null){
                    continue;
                }
                Vector3d point = copy(pointForce.point());
                Vector3d force = copy(pointForce.force()).mul(scale);
                Vector3d worldPoint = pose == null
                        ? new Vector3d(point)
                        : pose.transformPosition(point, new Vector3d());
                Vector3d worldForce = pose == null
                        ? new Vector3d(force)
                        : pose.transformNormal(force, new Vector3d());
                samples.add(new ForceSample(
                        groupId,
                        groupName,
                        group.color(),
                        point,
                        force,
                        worldPoint,
                        worldForce));
            }
        }
        return samples;
    }

    // Copy the sampled physics value
    private static Vector3d copy(@Nullable Vector3dc vector){
        return vector == null ? new Vector3d() : new Vector3d(vector);
    }

    // Copy the sampled physics value
    private static Quaterniond copy(@Nullable Quaterniondc quaternion){
        return quaternion == null ? new Quaterniond() : new Quaterniond(quaternion);
    }

    // Copy the sampled physics value
    private static Matrix3d copy(@Nullable Matrix3dc matrix){
        return matrix == null ? new Matrix3d() : new Matrix3d(matrix);
    }

    // Normalize the value to a finite result
    private static double finite(double val){
        return Double.isFinite(val) ? val : 0.0D;
    }

    // Store the cached snapshot
    private record CachedSnapshot(long gameTime, Snapshot snapshot){
    }

    // Store the force sample
    public record ForceSample(String groupId, String groupName, int color,
                               Vector3d point, Vector3d force,
                               Vector3d worldPoint, Vector3d worldForce){
        // Own independent force vectors at the API boundary
        public ForceSample{
            point = new Vector3d(point); force = new Vector3d(force);
            worldPoint = new Vector3d(worldPoint); worldForce = new Vector3d(worldForce);
        }
        // Return independent force coordinates
        @Override public Vector3d point(){ return new Vector3d(point); }
        // Return an independent local force
        @Override public Vector3d force(){ return new Vector3d(force); }
        // Return independent world coordinates
        @Override public Vector3d worldPoint(){ return new Vector3d(worldPoint); }
        // Return an independent world force
        @Override public Vector3d worldForce(){ return new Vector3d(worldForce); }
    }

    // Store the snapshot
    public record Snapshot(boolean available,
                            @Nullable UUID subLevelId,
                            String subLevelName,
                            double mass,
                            double inverseMass,
                            Matrix3d inertiaTensor,
                            Vector3d centerOfMass,
                            Vector3d worldCenterOfMass,
                            Vector3d position,
                            Quaterniond orientation,
                            Vector3d linearVelocity,
                            Vector3d angularVelocity,
                            Vector3d totalForce,
                            Vector3d worldTotalForce,
                            Vector3d totalTorque,
                            Vector3d worldTotalTorque,
                            List<ForceSample> forces){
        // Copy mutable physics values before exposing the snapshot
        public Snapshot{
            inertiaTensor = new Matrix3d(inertiaTensor); orientation = new Quaterniond(orientation);
            forces = List.copyOf(forces);
            centerOfMass = new Vector3d(centerOfMass);
            worldCenterOfMass = new Vector3d(worldCenterOfMass);
            position = new Vector3d(position);
            linearVelocity = new Vector3d(linearVelocity);
            angularVelocity = new Vector3d(angularVelocity);
            totalForce = new Vector3d(totalForce);
            worldTotalForce = new Vector3d(worldTotalForce);
            totalTorque = new Vector3d(totalTorque);
            worldTotalTorque = new Vector3d(worldTotalTorque);
        }
        // Return an independent inertia tensor
        @Override public Matrix3d inertiaTensor(){ return new Matrix3d(inertiaTensor); }
        // Return an independent orientation
        @Override public Quaterniond orientation(){ return new Quaterniond(orientation); }
        // Return independent sampled coordinates
        @Override public Vector3d centerOfMass(){ return new Vector3d(centerOfMass); }
        // Return independent sampled coordinates
        @Override public Vector3d worldCenterOfMass(){ return new Vector3d(worldCenterOfMass); }
        // Return independent sampled coordinates
        @Override public Vector3d position(){ return new Vector3d(position); }
        // Return independent sampled coordinates
        @Override public Vector3d linearVelocity(){ return new Vector3d(linearVelocity); }
        // Return independent sampled coordinates
        @Override public Vector3d angularVelocity(){ return new Vector3d(angularVelocity); }
        // Return independent sampled coordinates
        @Override public Vector3d totalForce(){ return new Vector3d(totalForce); }
        // Return independent sampled coordinates
        @Override public Vector3d worldTotalForce(){ return new Vector3d(worldTotalForce); }
        // Return independent sampled coordinates
        @Override public Vector3d totalTorque(){ return new Vector3d(totalTorque); }
        // Return independent sampled coordinates
        @Override public Vector3d worldTotalTorque(){ return new Vector3d(worldTotalTorque); }
        // Create an unavailable snapshot
        public static Snapshot unavailable(@Nullable UUID subLevelId){
            return new Snapshot(
                    false,
                    subLevelId,
                    "",
                    0.0D,
                    0.0D,
                    new Matrix3d(),
                    new Vector3d(),
                    new Vector3d(),
                    new Vector3d(),
                    new Quaterniond(),
                    new Vector3d(),
                    new Vector3d(),
                    new Vector3d(),
                    new Vector3d(),
                    new Vector3d(),
                    new Vector3d(),
                    List.of());
        }
    }
}
