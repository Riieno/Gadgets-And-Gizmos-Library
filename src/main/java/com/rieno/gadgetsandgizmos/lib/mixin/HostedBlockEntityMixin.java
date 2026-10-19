package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Persist detached component changes through their actual owning block
@Mixin(BlockEntity.class)
public abstract class HostedBlockEntityMixin{
    @Inject(method = "setChanged()V", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$notifyHost(CallbackInfo ci){
        if(HostedBlockEntities.notifyHost((BlockEntity) (Object) this, false)) ci.cancel();
    }
}
