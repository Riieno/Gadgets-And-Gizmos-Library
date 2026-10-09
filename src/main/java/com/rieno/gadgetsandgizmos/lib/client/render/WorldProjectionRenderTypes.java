package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;

// Keep projected information visible on both sides without writing into world depth
public final class WorldProjectionRenderTypes extends RenderStateShard{
    private static final DepthTestStateShard DEPTH = new ProjectionDepth();
    private static final RenderType COLOR = RenderType.create("gadgetsngizmos_world_projection",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 2048, false, false,
            RenderType.CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(DEPTH).setCullState(NO_CULL).createCompositeState(false));

    private WorldProjectionRenderTypes(){ super("gadgetsngizmos_world_projection", () -> {}, () -> {}); }

    // Render caller-owned color quads in an already transformed world projection
    public static RenderType color(){ return COLOR; }

    // Disable an inherited world depth test and restore it after the overlay draw
    private static final class ProjectionDepth extends DepthTestStateShard{
        private boolean enabled;
        private ProjectionDepth(){ super("projection", GL11.GL_ALWAYS); }
        @Override public void setupRenderState(){
            enabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            RenderSystem.disableDepthTest();
        }
        @Override public void clearRenderState(){
            if(enabled) RenderSystem.enableDepthTest();
        }
    }
}
