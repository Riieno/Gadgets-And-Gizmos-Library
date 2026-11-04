package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.function.Supplier;

// Share ordinary and additive effect blending across client compatibility renderers
public final class ColorEffectRenderTypes extends RenderStateShard{
    private static final TransparencyStateShard ADDITIVE = new TransparencyStateShard("gadgetsngizmos_effect_additive",
            () -> { RenderSystem.enableBlend(); RenderSystem.blendFuncSeparate(770, 1, 1, 1); },
            () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
    private static final RenderType COLOR = color(false);
    private static final RenderType COLOR_ADDITIVE = color(true);

    private ColorEffectRenderTypes(){ super("gadgetsngizmos_color_effect", () -> {}, () -> {}); }

    public static RenderType color(boolean additive){
        return RenderType.create("gadgetsngizmos_color_effect_" + additive, DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 4096, false, true,
                RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(additive ? ADDITIVE : TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL).createCompositeState(false));
    }

    public static RenderType untextured(boolean additive){ return additive ? COLOR_ADDITIVE : COLOR; }

    public static RenderType shader(String name, VertexFormat format, Supplier<ShaderInstance> shader, boolean additive){
        return RenderType.create(name, format, VertexFormat.Mode.QUADS, 4096, false, true,
                RenderType.CompositeState.builder().setShaderState(new ShaderStateShard(shader))
                        .setTransparencyState(additive ? ADDITIVE : TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL).createCompositeState(false));
    }

    public static void copyFloatUniform(ShaderInstance source, ShaderInstance target, String name){
        if(source == null || target == null) return;
        var from = source.getUniform(name);
        var to = target.getUniform(name);
        if(from != null && to != null && from.getFloatBuffer() != null) to.set(from.getFloatBuffer().get(0));
    }
}
