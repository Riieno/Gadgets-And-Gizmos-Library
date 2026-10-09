package com.rieno.gadgetsandgizmos.lib.client.view;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

// Keep projected control geometry in the host's world transform and depth buffer
public record ProjectedViewControlCanvas(PoseStack stack, MultiBufferSource buffers,
                                         ResourceLocation whiteTexture, float z) implements ViewControlPanel.Canvas{
    @Override public void fill(int left, int top, int right, int bottom, int color){
        if(right <= left || bottom <= top) return;
        var vertices = buffers.getBuffer(RenderType.text(whiteTexture));
        var matrix = stack.last().pose();
        vertices.addVertex(matrix, left, top, z).setColor(color).setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, left, bottom, z).setColor(color).setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, bottom, z).setColor(color).setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, top, z).setColor(color).setUv(0.5F, 0.5F).setLight(LightTexture.FULL_BRIGHT);
    }
    @Override public void drawString(Font font, String text, int x, int y, int color, boolean shadow){
        stack.pushPose();
        stack.translate(0, 0, z - 0.005F);
        font.drawInBatch(text, x, y, color, shadow, stack.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        stack.popPose();
    }
}
