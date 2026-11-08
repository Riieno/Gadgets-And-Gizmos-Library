package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.rieno.gadgetsandgizmos.lib.physics.PhysicsBodyAvailability;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reject direct and deferred wakes after a ship has left the native physics scene
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class SablePhysicsBodyMixin implements PhysicsBodyAvailability{
    @Shadow @Final private Int2ObjectMap<ServerSubLevel> activeSubLevels;
    @Shadow @Final private ReferenceList<PhysicsPipelineBody> queuedWakeUps;
    @Unique private boolean gadgetsngizmos$sceneReady;

    // Publish availability only after the native scene has been created
    @Inject(method = "init", at = @At("RETURN"))
    private void gadgetsngizmos$sceneCreated(CallbackInfo ci){ gadgetsngizmos$sceneReady = true; }

    // The Java removed flag alone does not describe native body residency
    @Override
    public boolean isBodyLoaded(PhysicsPipelineBody body){
        return gadgetsngizmos$sceneReady && body != null && !body.isRemoved()
                && (!(body instanceof ServerSubLevel server) || activeSubLevels.get(server.getRuntimeId()) == server);
    }

    // A controller releasing outputs during unload must never wake an absent native body
    @Inject(method = "wakeUp", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$guardWake(PhysicsPipelineBody body, CallbackInfo ci){
        if(!isBodyLoaded(body)) ci.cancel();
    }

    // Recheck membership at dispatch because a queued request can outlive its body
    @ModifyExpressionValue(method = "physicsTick", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;isRemoved()Z"))
    private boolean gadgetsngizmos$guardQueuedWake(boolean removed, @Local PhysicsPipelineBody body){
        return removed || !isBodyLoaded(body);
    }

    // Remove requests queued before or during body cleanup
    @Inject(method = "remove(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V", at = @At("RETURN"))
    private void gadgetsngizmos$forgetWake(ServerSubLevel body, CallbackInfo ci){
        queuedWakeUps.removeIf(queued -> queued == body);
    }

    // Old requests cannot be carried into a replacement physics scene
    @Inject(method = "dispose", at = @At("HEAD"))
    private void gadgetsngizmos$sceneDisposed(CallbackInfo ci){
        gadgetsngizmos$sceneReady = false;
        queuedWakeUps.clear();
    }
}
