package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities;
import com.rieno.gadgetsandgizmos.lib.physics.BlockEntityBindings;
import com.rieno.gadgetsandgizmos.lib.network.BlockEntityDataSync;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Synchronize hosted Create components without rebuilding the host's block mesh
@Mixin(value = SyncedBlockEntity.class, remap = false)
public abstract class HostedSmartBlockEntityMixin{
    @Inject(method = "sendData", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$syncHost(CallbackInfo ci){
        if(!((Object) this instanceof SmartBlockEntity)) return;
        BlockEntity component = (BlockEntity) (Object) this;
        if(HostedBlockEntities.notifyHostData(component, true)){ ci.cancel(); return; }
        if(BlockEntityBindings.host(component) != null){
            BlockEntityDataSync.enqueue(component);
            ci.cancel();
        }
    }
}
