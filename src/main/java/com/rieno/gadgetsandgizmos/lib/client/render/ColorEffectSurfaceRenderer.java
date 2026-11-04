package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

// Draw a saved color effect on a caller-positioned screen while preserving world occlusion
public final class ColorEffectSurfaceRenderer extends RenderStateShard{
    private static final int GRADIENT_SEGMENTS = 64;
    private static final RenderType SURFACE = RenderType.create("gadgetsngizmos_color_effect_surface",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(NO_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE).setCullState(CULL).createCompositeState(false));

    private ColorEffectSurfaceRenderer(){ super("gadgetsngizmos_color_effect_surface", () -> {}, () -> {}); }

    // Use local XY screen coordinates, with Y down and the visible face toward negative Z
    public static void render(PoseStack pose, MultiBufferSource buffers, ColorEffectPreset preset,
                              double time, float width, float height){
        if(!visible(preset, width, height)) return;
        draw(pose.last().pose(), buffers.getBuffer(SURFACE), preset, time, width, height);
    }

    private static boolean visible(ColorEffectPreset preset, float width, float height){
        return preset != null && preset.active() && Float.isFinite(width) && Float.isFinite(height)
                && width > 0 && height > 0;
    }

    // Sample the same animation rules as the rendered effect, including static gradients
    static void draw(Matrix4f pose, VertexConsumer vertices, ColorEffectPreset preset,
                     double time, float width, float height){
        if(!visible(preset, width, height)) return;
        if(!Double.isFinite(time)) time = 0;
        int segments = preset.mode() == ColorEffectPreset.Mode.GRADIENT ? GRADIENT_SEGMENTS : 1;
        for(int index = 0; index < segments; index++){
            float start = index / (float)segments, end = (index + 1) / (float)segments;
            int left = 0xFF000000 | preset.sample(time, start, 0);
            int right = 0xFF000000 | preset.sample(time, end, 0);
            vertices.addVertex(pose, start * width, 0, 0).setColor(left);
            vertices.addVertex(pose, start * width, height, 0).setColor(left);
            vertices.addVertex(pose, end * width, height, 0).setColor(right);
            vertices.addVertex(pose, end * width, 0, 0).setColor(right);
        }
    }
}
