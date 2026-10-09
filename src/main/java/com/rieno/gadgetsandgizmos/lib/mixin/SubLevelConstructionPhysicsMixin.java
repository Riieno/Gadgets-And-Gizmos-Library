package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelConstructionState;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep actor ticks and networked poses stable until construction releases the assembly
@Mixin(SubLevelPhysicsSystem.class)
public abstract class SubLevelConstructionPhysicsMixin{
    // Delay native actor state changes while block entity ticks are paused
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/block/BlockEntitySubLevelActor;sable$tick(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V"))
    private void gadgetsngizmos$delayConstructionActors(BlockEntitySubLevelActor actor, ServerSubLevel body, Operation<Void> original){
        if(!SubLevelConstructionState.isBuilding(body)) original.call(actor, body);
    }

    // Retain the intended world transform while native physics steps an unfinished body
    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$holdConstructionPose(ServerSubLevel body, CallbackInfo ci){
        var pose = SubLevelConstructionState.pose(body);
        if(pose == null) return;
        body.logicalPose().set(pose);
        body.latestLinearVelocity.zero(); body.latestAngularVelocity.zero();
        ci.cancel();
    }
}
