package com.rieno.gadgetsandgizmos.lib.compat.simulated;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmDockingOrientation;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Objects;
import java.util.UUID;

// Restore persisted Simulated connector links without loading their peer chunks.
public final class SimulatedDockingApi{
    private static final double TIP_OFFSET = 1.5D;
    private SimulatedDockingApi(){}

    // Opt an endpoint into stepped roll alignment; zero retains the native policy
    public interface AlignmentPolicy{
        double dockingRollStepDegrees();
    }

    // Supply the same stepped pose to the native magnet torque and locking constraint
    public static boolean resolveAlignment(BlockEntity first, BlockEntity second, Quaterniond target, Vector3d error){
        double step = first instanceof AlignmentPolicy policy ? policy.dockingRollStepDegrees() : 0;
        if(step <= 0 && second instanceof AlignmentPolicy policy) step = policy.dockingRollStepDegrees();
        if(!Double.isFinite(step) || step <= 0 || !(first instanceof DockingConnectorBlockEntity dock1)
                || !(second instanceof DockingConnectorBlockEntity dock2)) return false;
        var level1 = dock1.getLatestSubLevel(); var level2 = dock2.getLatestSubLevel();
        var body1 = level1 == null ? new Quaterniond() : new Quaterniond(level1.logicalPose().orientation());
        var body2 = level2 == null ? new Quaterniond() : new Quaterniond(level2.logicalPose().orientation());
        var block1 = new Quaterniond(dock1.getOrientation()); var block2 = new Quaterniond(dock2.getOrientation());
        var world1 = new Quaterniond(body1).mul(block1); var world2 = new Quaterniond(body2).mul(block2);
        Vec3 facing = direction(world1, 0, 1, 0), up = direction(world1, 0, 0, 1);
        Vec3 targetFacing = direction(world2, 0, -1, 0), targetUp = direction(world2, 0, 0, 1);
        Vec3 correction = ScmDockingOrientation.alignmentError(facing, up, targetFacing, targetUp, step);
        double angle = correction.length();
        var rotation = angle < 1.0E-12D ? new Quaterniond() : new Quaterniond().rotationAxis(angle,
                correction.x / angle, correction.y / angle, correction.z / angle);
        target.set(block1).mul(block2.conjugate()).mul(body2.conjugate()).mul(rotation.mul(body1, new Quaterniond())).normalize();
        error.set(rotation.x, rotation.y, rotation.z).mul(2.0D);
        return true;
    }

    private static Vec3 direction(Quaterniond rotation, double x, double y, double z){
        var value = rotation.transform(new Vector3d(x, y, z));
        return new Vec3(value.x, value.y, value.z);
    }

    // Resolve the base block centre from the extended contact point in the same coordinate frame
    public static Vec3 basePosition(Vec3 tip, Vec3 facing){
        return tip.subtract(facing.normalize().scale(TIP_OFFSET));
    }

    // Resolve the extended contact point from the base block centre in the same coordinate frame
    public static Vec3 tipPosition(Vec3 base, Vec3 facing){
        return base.add(facing.normalize().scale(TIP_OFFSET));
    }

    // Implemented by the compatibility mixin; logical lock state alone is not a physics joint.
    public interface PhysicsState{
        boolean hasDockingConstraint();
        boolean isDockingConstraintPending();
    }

    public static boolean isEndpointLoaded(@Nullable Level level, @Nullable UUID bodyId, BlockPos pos){
        if(level == null || pos == null) return false;
        if(bodyId == null) return level.hasChunkAt(pos);
        return SubLevelBlockEntityCollector.isTargetLoaded(level, bodyId, pos);
    }

    public static boolean isAwaitingPeer(@Nullable BlockEntity endpoint){
        if(!(endpoint instanceof DockingConnectorBlockEntity connector)
                || connector.otherConnectorPosition == null) return false;
        return !isEndpointLoaded(connector.getLevel(), connector.otherConnectorSubLevelId,
                connector.otherConnectorPosition);
    }

    public static boolean hasPhysicalConnection(@Nullable BlockEntity endpoint){
        if(!(endpoint instanceof DockingConnectorBlockEntity connector)
                || connector.otherConnectorPosition == null || isAwaitingPeer(connector)) return false;
        BlockEntity peer = loadedPeer(connector);
        return peer instanceof DockingConnectorBlockEntity other
                && Objects.equals(other.otherConnectorPosition, connector.getBlockPos())
                && sameSavedBodies(connector, other)
                && (hasConstraint(connector) || hasConstraint(other));
    }

    // Retry the upstream initialize-time restoration when both saved endpoints become available.
    public static boolean restoreConnection(@Nullable BlockEntity endpoint){
        if(!(endpoint instanceof DockingConnectorBlockEntity connector)
                || connector.getLevel() == null || connector.getLevel().isClientSide
                || connector.otherConnectorPosition == null || isAwaitingPeer(connector)) return false;
        BlockEntity peer = loadedPeer(connector);
        if(!(peer instanceof DockingConnectorBlockEntity other)
                || !Objects.equals(other.otherConnectorPosition, connector.getBlockPos())
                || !sameSavedBodies(connector, other)) return false;
        if(hasConstraint(connector) || hasConstraint(other)) return true;
        if(isPending(connector) || isPending(other)) return false;
        var controller = DockingConnectorBlockEntity.MAGNET_CONTROLLER;
        controller.tryAddPair(connector.getLevel(), connector.getBlockPos(),
                other.getBlockPos(), DockingConnectorPair::new);
        var pair = controller.getPair(connector.getLevel(), connector.getBlockPos(), other.getBlockPos());
        if(pair instanceof DockingConnectorPair dockingPair) dockingPair.dock(true);
        return hasPhysicalConnection(connector);
    }

    private static boolean sameSavedBodies(DockingConnectorBlockEntity connector, DockingConnectorBlockEntity other){
        return Objects.equals(connector.otherConnectorSubLevelId, SableLevelApi.containingId(other))
                && Objects.equals(other.otherConnectorSubLevelId, SableLevelApi.containingId(connector));
    }

    // Resolve native and actor-backed connector endpoints in already-loaded plots
    public static @Nullable DockingConnectorBlockEntity loadedPeer(@Nullable BlockEntity endpoint){
        if(!(endpoint instanceof DockingConnectorBlockEntity connector) || connector.getLevel() == null
                || connector.otherConnectorPosition == null || isAwaitingPeer(connector)) return null;
        BlockEntity peer = connector.otherConnectorSubLevelId == null
                ? connector.getLevel().getBlockEntity(connector.otherConnectorPosition)
                : BlockEntityLookupApi.findLoadedExact(connector.getLevel(), connector.otherConnectorSubLevelId,
                        connector.otherConnectorPosition);
        return peer instanceof DockingConnectorBlockEntity other && !other.isRemoved() ? other : null;
    }

    private static boolean isPending(BlockEntity endpoint){
        return endpoint instanceof PhysicsState state && state.isDockingConstraintPending();
    }

    private static boolean hasConstraint(BlockEntity endpoint){
        return endpoint instanceof PhysicsState state && state.hasDockingConstraint();
    }

}
