package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep feed lighting separate from shader packs and the player's render passes
@Mixin(GameRenderer.class)
public abstract class ViewSceneLightmapMixin{
    // Bind the vanilla lightmap owned and refreshed by the active feed
    @Inject(method = "lightTexture", at = @At("HEAD"), cancellable = true)
    private void captureLightmap(CallbackInfoReturnable<LightTexture> cir){
        LightTexture light = ViewSceneRenderer.captureLightmap();
        if(light != null) cir.setReturnValue(light);
    }
}
