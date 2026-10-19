package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.render.DynamicRenderBuffers;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Skip deferred lighting when the active renderer cannot provide its scene buffers
@Mixin(value = LightRenderer.class, remap = false)
public abstract class VeilLightRendererMixin{
    @Shadow @Final private static ResourceLocation BUFFER_ID;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;bind(Z)V", ordinal = 0),
            cancellable = true, require = 0)
    private void gadgetsngizmos$requireSceneBuffers(CallbackInfoReturnable<Boolean> cir){
        if(!DynamicRenderBuffers.enableForFrame(VeilRenderSystem.renderer(), BUFFER_ID,
                DynamicBufferType.ALBEDO, DynamicBufferType.NORMAL)) cir.setReturnValue(false);
    }
}
