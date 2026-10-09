package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewShaderCompat;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Compile secondary terrain shaders against the active optional mesh layout
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class ViewSceneSodiumShaderMixin{
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void decodeViewTerrain(ResourceLocation name, CallbackInfoReturnable<String> cir){
        cir.setReturnValue(ViewShaderCompat.terrainShader(name, cir.getReturnValue()));
    }
}
