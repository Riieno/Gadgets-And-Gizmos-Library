package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.mixin.ViewSceneRenderSystemAccess;

// Keep shader samplers from retaining textures after their owner releases them
public final class ShaderTextureBindings{
    private ShaderTextureBindings(){}

    // Clear every matching sampler on the render thread without changing texture ownership
    public static void clearReferences(int texture){
        RenderSystem.assertOnRenderThreadOrInit();
        if(texture <= 0) return;
        int[] textures = ViewSceneRenderSystemAccess.gadgetsngizmos$textures();
        for(int idx = 0; idx < textures.length; idx++){
            if(textures[idx] == texture) textures[idx] = 0;
        }
    }
}
