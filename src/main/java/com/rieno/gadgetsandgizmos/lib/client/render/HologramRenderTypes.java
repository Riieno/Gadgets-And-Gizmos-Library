package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

// Retain depth and back-face rejection between translucent miniature terrain blocks
public final class HologramRenderTypes extends RenderStateShard{
    private static final RenderType TERRAIN = RenderType.create("gadgetsngizmos_hologram_terrain",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 262144, false, false,
            RenderType.CompositeState.builder().setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, true))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setLightmapState(LIGHTMAP).createCompositeState(false));

    private HologramRenderTypes(){ super("gadgetsngizmos_hologram", () -> {}, () -> {}); }
    public static RenderType terrain(){ return TERRAIN; }
}
