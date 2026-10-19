package com.rieno.gadgetsandgizmos.lib.compat.simulated;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

// Restore persisted Simulated connector links without loading their peer chunks.
public final class SimulatedDockingApi{
    private SimulatedDockingApi(){}

    // Implemented by the compatibility mixin; logical lock state alone is not a physics joint.
    public interface PhysicsState{
        boolean hasDockingConstraint();
        boolean isDockingConstraintPending();
    }

    public static boolean isEndpointLoaded(@Nullable Level level, @Nullable UUID bodyId, BlockPos pos){
        if(level == null || pos == null || !level.hasChunkAt(pos)) return false;
        return bodyId == null || SableLevelApi.subLevel(level, bodyId) != null;
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
        BlockEntity peer = connector.getLevel().getBlockEntity(connector.otherConnectorPosition);
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
        BlockEntity peer = connector.getLevel().getBlockEntity(connector.otherConnectorPosition);
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

    private static boolean isPending(BlockEntity endpoint){
        return endpoint instanceof PhysicsState state && state.isDockingConstraintPending();
    }

    private static boolean hasConstraint(BlockEntity endpoint){
        return endpoint instanceof PhysicsState state && state.hasDockingConstraint();
    }

}
