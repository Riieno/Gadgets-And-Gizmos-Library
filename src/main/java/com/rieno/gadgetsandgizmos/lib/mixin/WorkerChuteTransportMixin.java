package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.content.logistics.chute.SmartChuteBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Pause normal and smart chutes without changing their saved redstone state
@Mixin({ChuteBlockEntity.class, SmartChuteBlockEntity.class})
public abstract class WorkerChuteTransportMixin{
    @Inject(method = "canActivate", at = @At("HEAD"), cancellable = true)
    private void ct$pauseWorkerChute(CallbackInfoReturnable<Boolean> cir){
        ChuteBlockEntity self = (ChuteBlockEntity)(Object)this;
        if(WorkerTransportLocks.isLocked(self.getLevel(), self.getBlockPos())) cir.setReturnValue(false);
    }
}
