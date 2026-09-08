package com.rieno.gadgetsandgizmos.lib.kinetics;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A reciprocal, loaded, 1:1 connection in Create's ordinary kinetic network. */
public abstract class LinkedKineticBlockEntity extends KineticBlockEntity {
    @Nullable
    private LinkedKineticBlockEntity connectedEndpoint;
    private boolean linkInitialized;
    private boolean rebuildSavedNetwork;

    protected LinkedKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state){
        super(type, pos, state);
    }

    /** Resolve the saved endpoint without loading chunks; return null when unavailable. */
    @Nullable
    protected abstract LinkedKineticBlockEntity resolveKineticLink();

    @Override
    public void initialize(){
        rebuildSavedKineticNetwork();
        super.initialize();
    }

    private void rebuildSavedKineticNetwork(){
        if(rebuildSavedNetwork && level != null && !level.isClientSide){
            rebuildSavedNetwork = false;
            lastCapacityProvided = 0;
            lastStressApplied = 0;
            if(hasNetwork()){
                KineticNetwork network = getOrCreateNetwork();
                // Old aggregate totals cannot identify unloaded generators or loads reliably.
                network.sources.entrySet().removeIf(entry -> !entry.getKey().isSource());
                network.sources.replaceAll((member, value) -> member.calculateAddedStressCapacity());
                network.members.replaceAll((member, value) -> member.calculateStressApplied());
                network.initFromTE(0, 0, 0);
            }
        }
    }

    @Override
    public void tick(){
        if(level != null && !level.isClientSide) refreshKineticLink();
        super.tick();
    }

    /** Rebuild legacy generated-network totals from loaded members before restoring the connection. */
    protected final void rebuildKineticNetworkOnLoad(){
        rebuildSavedNetwork = true;
    }

    /** Call after changing the saved endpoint, on the owning server thread. */
    public final void refreshKineticLink(){
        if(level == null || level.isClientSide || isRemoved()) return;
        rebuildSavedKineticNetwork();
        LinkedKineticBlockEntity next = resolveKineticLink();
        if(next == this || next != null && (next.isRemoved() || next.getLevel() != level
                || !level.isLoaded(next.getBlockPos())
                || next.resolveKineticLink() != this)) next = null;
        if(linkInitialized && next == connectedEndpoint) return;

        disconnectKineticLink();
        if(next != null){
            next.disconnectKineticLink();
            connectedEndpoint = next;
            next.connectedEndpoint = this;
        }
        setChanged();
        sendData();
    }

    private void disconnectKineticLink(){
        rebuildSavedKineticNetwork();
        LinkedKineticBlockEntity previous = connectedEndpoint;
        // Detach while the old edge is still visible to Create's source removal traversal.
        detachKinetics();
        removeSource();
        connectedEndpoint = null;
        linkInitialized = true;
        updateSpeed = true;
        if(previous != null && previous.connectedEndpoint == this){
            if(!previous.isRemoved() && level.isLoaded(previous.getBlockPos())){
                previous.detachKinetics();
                previous.removeSource();
            }
            previous.connectedEndpoint = null;
            previous.updateSpeed = true;
        }
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours){
        List<BlockPos> positions = super.addPropagationLocations(block, state, neighbours);
        if(connectedEndpoint != null && level != null && level.isLoaded(connectedEndpoint.getBlockPos())
                && !positions.contains(connectedEndpoint.getBlockPos())){
            positions.add(connectedEndpoint.getBlockPos());
        }
        return positions;
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState){
        return other == connectedEndpoint || super.isCustomConnection(other, state, otherState);
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo,
                                     BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs){
        return target == connectedEndpoint ? 1.0f
                : super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    @Override
    public float calculateAddedStressCapacity(){
        return lastCapacityProvided = 0;
    }

    @Override
    public float calculateStressApplied(){
        return lastStressApplied = 0;
    }
}
