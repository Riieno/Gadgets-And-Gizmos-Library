package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicalConnectionApi;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Retire collision ownership when a native joint is removed
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle", remap = false)
public abstract class SableConstraintRemovalMixin{
    @Inject(method = "remove", at = @At("RETURN"))
    private void gadgetsngizmos$releaseConnection(CallbackInfo ci){
        SablePhysicalConnectionApi.untrack((PhysicsConstraintHandle)(Object)this);
    }
}
