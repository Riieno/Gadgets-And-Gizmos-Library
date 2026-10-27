package com.rieno.gadgetsandgizmos.lib.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.rieno.gadgetsandgizmos.lib.client.render.ShaderTextureBindings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Retire shader sampler references alongside Minecraft's texture binding cache
@Mixin(GlStateManager.class)
public abstract class ShaderTextureReleaseMixin{
    @Inject(method = "_deleteTexture", at = @At("HEAD"))
    private static void clearTexture(int texture, CallbackInfo ci){
        ShaderTextureBindings.clearReferences(texture);
    }

    @Inject(method = "_deleteTextures", at = @At("HEAD"))
    private static void clearTextures(int[] textures, CallbackInfo ci){
        for(int texture : textures) ShaderTextureBindings.clearReferences(texture);
    }
}
