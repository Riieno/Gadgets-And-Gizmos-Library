package com.rieno.gadgetsandgizmos.lib.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Release a feed's texture registration alongside its lightmap allocation
@Mixin(LightTexture.class)
public interface ViewSceneLightmapAccess{
    @Accessor("lightTextureLocation")
    ResourceLocation gadgetsngizmos$lightmapLocation();
}
