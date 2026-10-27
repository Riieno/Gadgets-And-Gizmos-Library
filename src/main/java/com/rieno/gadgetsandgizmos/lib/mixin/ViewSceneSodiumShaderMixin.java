package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewShaderCompat;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// Compile secondary terrain shaders against the active optional mesh layout
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class ViewSceneSodiumShaderMixin{
    // Compose with other shader patches without cancelling their return handlers
    @ModifyReturnValue(method = "getShaderSource", at = @At("RETURN"), require = 0, remap = false)
    private static String decodeViewTerrain(String src, ResourceLocation name){
        return ViewShaderCompat.terrainShader(name, src);
    }
}
