package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

// Remove texture saturation before applying the model's vertex tint
public final class DesaturatedModelRenderTypes extends RenderStateShard{
    private static final ResourceLocation SHADER_ID = ResourceLocation.fromNamespaceAndPath(
            GadgetsNGizmosLibrary.MOD_ID, "desaturated_model");
    private static ShaderInstance shader;
    private static final RenderType CUTOUT = RenderType.create("gadgetsngizmos_desaturated_model",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader))
                    .setTextureState(BLOCK_SHEET_MIPPED).setLightmapState(LIGHTMAP).createCompositeState(false));

    private DesaturatedModelRenderTypes(){ super("gadgetsngizmos_desaturated_model", () -> {}, () -> {}); }

    public static RenderType cutoutMipped(){ return CUTOUT; }

    public static void registerShader(RegisterShadersEvent evt) throws IOException{
        evt.registerShader(new ShaderInstance(evt.getResourceProvider(), SHADER_ID, DefaultVertexFormat.BLOCK), val -> shader = val);
    }
}
