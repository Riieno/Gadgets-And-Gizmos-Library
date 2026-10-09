package com.rieno.gadgetsandgizmos.lib.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Include texture slots added by optional renderers when retaining scene state
@Mixin(RenderSystem.class)
public interface ViewSceneRenderSystemAccess{
    @Accessor("shaderTextures")
    static int[] gadgetsngizmos$textures(){ throw new AssertionError(); }
    // Preserve item preview lighting when projected GUIs render during a world draw
    @Accessor("shaderLightDirections")
    static Vector3f[] gadgetsngizmos$lights(){ throw new AssertionError(); }
}
