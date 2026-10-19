package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicalConnectionApi;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Refresh attachment validity after native physics publishes its next state
@Mixin(SubLevelPhysicsSystem.class)
public abstract class SableCollisionPhysicsFrameMixin{
    @Inject(method = "tick", at = @At("RETURN"))
    private void gadgetsngizmos$publishCollisionFrame(SubLevelContainer container, CallbackInfo ci){
        SablePhysicalConnectionApi.invalidate(((SubLevelPhysicsSystem)(Object)this).getLevel());
    }
}
