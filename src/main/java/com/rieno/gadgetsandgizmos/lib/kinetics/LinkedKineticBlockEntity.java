package com.rieno.gadgetsandgizmos.lib.kinetics;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reciprocal, loaded connections in Create's ordinary kinetic network. */
public abstract class LinkedKineticBlockEntity extends KineticBlockEntity {
    private final Set<LinkedKineticBlockEntity> connectedEndpoints = new LinkedHashSet<>();
    private boolean linkInitialized;
    private boolean rebuildSavedNetwork;

    protected LinkedKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state){
        super(type, pos, state);
    }

    /** Resolve the saved endpoint without loading chunks; return null when unavailable. */
    @Nullable
    protected abstract LinkedKineticBlockEntity resolveKineticLink();

    /** Override for blocks that can have several independent links. */
    protected Collection<? extends LinkedKineticBlockEntity> resolveKineticLinks(){
        LinkedKineticBlockEntity endpoint = resolveKineticLink();
        return endpoint == null ? List.of() : List.of(endpoint);
    }

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
        Set<LinkedKineticBlockEntity> next = new LinkedHashSet<>();
        for(LinkedKineticBlockEntity endpoint : resolveKineticLinks()){
            if(endpoint != null && endpoint != this && !endpoint.isRemoved()
                    && endpoint.getLevel() == level && level.isLoaded(endpoint.getBlockPos())
                    && endpoint.resolveKineticLinks().contains(this)) next.add(endpoint);
        }
        if(linkInitialized && next.equals(connectedEndpoints)) return;

        Set<LinkedKineticBlockEntity> affected = new LinkedHashSet<>(connectedEndpoints);
        affected.addAll(next);
        // Remove the old source traversal while its edges are still visible.
        detachKinetics();
        removeSource();
        for(LinkedKineticBlockEntity endpoint : affected){
            if(!endpoint.isRemoved() && level.isLoaded(endpoint.getBlockPos())){
                endpoint.detachKinetics();
                endpoint.removeSource();
            }
            endpoint.connectedEndpoints.remove(this);
            endpoint.updateSpeed = true;
        }
        connectedEndpoints.clear();
        connectedEndpoints.addAll(next);
        for(LinkedKineticBlockEntity endpoint : next) endpoint.connectedEndpoints.add(this);
        linkInitialized = true;
        updateSpeed = true;
        setChanged();
        sendData();
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours){
        List<BlockPos> positions = super.addPropagationLocations(block, state, neighbours);
        for(LinkedKineticBlockEntity endpoint : connectedEndpoints){
            if(level != null && level.isLoaded(endpoint.getBlockPos())
                    && !positions.contains(endpoint.getBlockPos())) positions.add(endpoint.getBlockPos());
        }
        return positions;
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState){
        return connectedEndpoints.contains(other) || super.isCustomConnection(other, state, otherState);
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo,
                                     BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs){
        return connectedEndpoints.contains(target) ? 1.0f
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
