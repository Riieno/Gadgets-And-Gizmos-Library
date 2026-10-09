package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelConstructionState;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep native sublevels stable while their blocks are constructed over several ticks
@Mixin(ServerSubLevel.class)
public abstract class SubLevelConstructionMixin{
    // Initialize finite native physics mass before an empty body reaches its first layer
    @Inject(method = "getMassTracker", at = @At("RETURN"), cancellable = true)
    private void gadgetsngizmos$constructionMass(CallbackInfoReturnable<MassData> ci){
        ci.setReturnValue(SubLevelConstructionState.mass((ServerSubLevel)(Object)this, ci.getReturnValue()));
    }

    // Empty plots have no meaningful world bounds before their first scheduled block
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$waitForConstructionBounds(CallbackInfo ci){
        ServerSubLevel body = (ServerSubLevel)(Object)this;
        var bounds = body.getPlot().getBoundingBox();
        if(SubLevelConstructionState.isBuilding(body) && (bounds.minX() > bounds.maxX()
                || bounds.minY() > bounds.maxY() || bounds.minZ() > bounds.maxZ())) ci.cancel();
    }

    // Preserve temporarily empty plots until their scheduled layer is placed
    @Inject(method = "onPlotBoundsChanged", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$preserveConstruction(CallbackInfo ci){
        if(SubLevelConstructionState.isBuilding((ServerSubLevel)(Object)this)) ci.cancel();
    }

    // Delay splitting until all neighboring schematic blocks are present
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/plot/heat/SubLevelHeatMapManager;tick()V"))
    private void gadgetsngizmos$delaySplitting(SubLevelHeatMapManager manager, Operation<Void> original){
        if(!SubLevelConstructionState.isBuilding((ServerSubLevel)(Object)this)) original.call(manager);
    }

    // Hold the construction pose and suppress actor forces until the assembly is complete
    @Inject(method = "prePhysicsTick", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$holdConstruction(SubLevelPhysicsSystem system, RigidBodyHandle handle, double timeStep, CallbackInfo ci){
        ServerSubLevel body = (ServerSubLevel)(Object)this;
        var pose = SubLevelConstructionState.pose(body);
        if(pose == null) return;
        body.logicalPose().set(pose);
        if(handle != null && handle.isValid()){
            handle.teleport(pose.position(), pose.orientation());
            handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(),
                    handle.getAngularVelocity(new Vector3d()).negate());
        }
        ci.cancel();
    }
}
