package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderer;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Resolve sub-level compilation and drawing through the active camera's dispatcher
@Mixin(SubLevelRenderer.class)
public abstract class ViewSceneSubLevelDispatcherMixin{
    // Leave the global dispatcher attached to the player's view
    @Inject(method = "getDispatcher", at = @At("HEAD"), cancellable = true)
    private static void captureDispatcher(CallbackInfoReturnable<SubLevelRenderDispatcher> cir){
        var dispatcher = ViewSceneRenderer.captureSubLevelDispatcher();
        if(dispatcher != null) cir.setReturnValue(dispatcher);
    }
}
