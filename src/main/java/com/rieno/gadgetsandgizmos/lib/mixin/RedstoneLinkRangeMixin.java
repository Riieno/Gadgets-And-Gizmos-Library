package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.control.DistanceIndependentLink;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Apply remote endpoint range policy within Create's normal frequency network
@Mixin(value = RedstoneLinkNetworkHandler.class, remap = false)
public abstract class RedstoneLinkRangeMixin{
    // Preserve ordinary link range unless an endpoint explicitly supports remote signals
    @Inject(method = "withinRange", at = @At("HEAD"), cancellable = true)
    private static void gadgetsngizmos$remoteRange(IRedstoneLinkable from, IRedstoneLinkable to,
                                                  CallbackInfoReturnable<Boolean> ci){
        if(from instanceof DistanceIndependentLink || to instanceof DistanceIndependentLink) ci.setReturnValue(true);
    }
}
