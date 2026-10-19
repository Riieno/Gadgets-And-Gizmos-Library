package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Pause both belt extraction and belt pickup while a worker owns this funnel gate
@Mixin(FunnelBlockEntity.class)
public abstract class WorkerFunnelTransportMixin{
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ct$pauseWorkerFunnel(CallbackInfo ci){
        FunnelBlockEntity self = (FunnelBlockEntity)(Object)this;
        if(WorkerTransportLocks.isLocked(self.getLevel(), self.getBlockPos())) ci.cancel();
    }

    @Inject(method = "handleDirectBeltInput", at = @At("HEAD"), cancellable = true)
    private void ct$pauseWorkerBeltPickup(TransportedItemStack transported, Direction direction,
                                          boolean simulate, CallbackInfoReturnable<ItemStack> cir){
        FunnelBlockEntity self = (FunnelBlockEntity)(Object)this;
        if(WorkerTransportLocks.isLocked(self.getLevel(), self.getBlockPos()))
            cir.setReturnValue(transported.stack);
    }
}
