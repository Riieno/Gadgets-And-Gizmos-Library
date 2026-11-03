package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicalConnectionApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableFrameConstraint;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Include native stickers and other physical attachments in collision ownership
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class SablePhysicalConnectionMixin{
    @Inject(method = "addConstraint", at = @At("RETURN"))
    private void gadgetsngizmos$retainConnection(PhysicsPipelineBody first, PhysicsPipelineBody second,
            PhysicsConstraintConfiguration<?> cfg, CallbackInfoReturnable<PhysicsConstraintHandle> ci){
        if(first instanceof ServerSubLevel a && second instanceof ServerSubLevel b)
            SablePhysicalConnectionApi.track(a, b, ci.getReturnValue());
    }

    // Release owned joints and attachment edges before the native body leaves its scene
    @Inject(method = "remove(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V", at = @At("HEAD"))
    private void gadgetsngizmos$releaseConnections(ServerSubLevel body, CallbackInfo ci){
        SableFrameConstraint.releaseBody((PhysicsPipeline)(Object)this, body);
        SablePhysicalConnectionApi.untrack(body);
    }

    // Retire managed joints before Rapier frees the native scene
    @Inject(method = "dispose", at = @At("HEAD"))
    private void gadgetsngizmos$releaseScene(CallbackInfo ci){
        SableFrameConstraint.releasePipeline((PhysicsPipeline)(Object)this);
    }
}
