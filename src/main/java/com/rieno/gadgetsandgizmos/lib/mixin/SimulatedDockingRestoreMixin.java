package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedDockingApi;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DockingConnectorBlockEntity.class)
public abstract class SimulatedDockingRestoreMixin implements SimulatedDockingApi.PhysicsState{
    @Shadow private FixedConstraintHandle constraintHandle;
    @Unique private long gadgetsngizmos$constraintPendingUntil = Long.MIN_VALUE;
    @Unique private FixedConstraintHandle gadgetsngizmos$observedConstraint;
    @Unique private boolean gadgetsngizmos$restoreSavedConnection;

    @Override public boolean hasDockingConstraint(){
        return constraintHandle != null && constraintHandle.isValid();
    }

    @Override public boolean isDockingConstraintPending(){
        DockingConnectorBlockEntity connector = (DockingConnectorBlockEntity)(Object)this;
        if(connector.getLevel() == null) return false;
        if(constraintHandle != gadgetsngizmos$observedConstraint){
            gadgetsngizmos$observedConstraint = constraintHandle;
            gadgetsngizmos$constraintPendingUntil = connector.getLevel().getGameTime() + 20L;
        }
        return constraintHandle != null
                && connector.getLevel().getGameTime() <= gadgetsngizmos$constraintPendingUntil;
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void gadgetsngizmos$savedConnection(CompoundTag tag, HolderLookup.Provider registries,
            boolean clientPacket, CallbackInfo ci){
        if(!clientPacket) gadgetsngizmos$restoreSavedConnection =
                ((DockingConnectorBlockEntity)(Object)this).otherConnectorPosition != null;
    }

    // Native initialization also resolves the peer through this accessor.
    @Inject(method = "getOtherConnector", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$unloadedPeer(CallbackInfoReturnable<DockingConnectorBlockEntity> cir){
        DockingConnectorBlockEntity connector = (DockingConnectorBlockEntity)(Object)this;
        if(connector.getLevel() != null && !connector.getLevel().isClientSide
                && SimulatedDockingApi.isAwaitingPeer(connector)) cir.setReturnValue(null);
    }

    // Missing chunks must not be interpreted as a broken reciprocal connection.
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$restoreSavedDock(CallbackInfo ci){
        DockingConnectorBlockEntity connector = (DockingConnectorBlockEntity)(Object)this;
        if(connector.getLevel() == null || connector.getLevel().isClientSide) return;
        if(SimulatedDockingApi.isAwaitingPeer(connector)){
            gadgetsngizmos$restoreSavedConnection = true;
            ci.cancel();
            return;
        }
        if(gadgetsngizmos$restoreSavedConnection){
            SimulatedDockingApi.restoreConnection(connector);
            if(connector.otherConnectorPosition == null || SimulatedDockingApi.hasPhysicalConnection(connector))
                gadgetsngizmos$restoreSavedConnection = false;
        }
    }
}
