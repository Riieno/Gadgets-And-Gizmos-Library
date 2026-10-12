package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

// Present already lit scenes without interpreting framebuffer alpha as surface transparency
public final class SceneRenderTypes extends RenderStateShard{
    private static final ResourceLocation SHADER_ID = ResourceLocation.fromNamespaceAndPath(
            "gadgetsngizmos", "scene_texture");
    private static ShaderInstance shader;
    private static final ShaderStateShard SCENE_SHADER = new ShaderStateShard(() -> shader);

    private SceneRenderTypes(){ super("gadgetsngizmos_scene_texture", () -> {}, () -> {}); }

    // Retain this render type with the caller's texture until that scene is released
    public static RenderType opaqueTexture(ResourceLocation texture){
        return RenderType.create("gadgetsngizmos_scene_texture", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 1536, false, false,
                RenderType.CompositeState.builder().setShaderState(SCENE_SHADER)
                        .setTextureState(new TextureStateShard(texture, true, false))
                        .setTransparencyState(NO_TRANSPARENCY).setCullState(NO_CULL)
                        .createCompositeState(false));
    }

    // Reload the scene shader with the rest of Minecraft's client resources
    public static void registerShader(RegisterShadersEvent evt) throws IOException{
        evt.registerShader(new ShaderInstance(evt.getResourceProvider(), SHADER_ID,
                DefaultVertexFormat.POSITION_TEX_COLOR), val -> shader = val);
    }
}
