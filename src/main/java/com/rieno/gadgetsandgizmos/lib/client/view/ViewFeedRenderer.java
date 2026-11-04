package com.rieno.gadgetsandgizmos.lib.client.view;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramRenderScope;
import com.rieno.gadgetsandgizmos.lib.client.render.StyledText;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudWidgetRenderer;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;

// Draw a camera feed inside a host's transformed element bounds
public final class ViewFeedRenderer{
    private ViewFeedRenderer(){}

    // Flush GUI geometry around the captured scene's independent render type
    public static void draw(GuiGraphics graphics, Font font, @Nullable ViewReference ref,
                            int x, int y, int width, int height){
        if(HologramRenderScope.isActive()) return;
        if(width <= 0 || height <= 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        background(HudWidgetRenderer.gui(graphics, font), width, height);
        graphics.pose().popPose();
        graphics.flush();
        graphics.pose().pushPose();
        boolean ready;
        try{
            graphics.pose().translate(x, y, 0);
            ready = ref != null && ViewSceneRenderer.drawFitted(ref, width, height, 0,
                    graphics.pose(), graphics.bufferSource());
            graphics.flush();
        }finally{
            graphics.pose().popPose();
        }
        if(ready) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        placeholder(HudWidgetRenderer.gui(graphics, font), font, ref, width, height);
        graphics.pose().popPose();
    }

    // Keep letterboxed feeds and unavailable cameras identical in GUI and world widgets
    public static void background(HudWidgetRenderer.Canvas canvas, int width, int height){
        canvas.quad(0, 0, 0, height, width, height, width, 0, 0xFF101A20, 0);
    }

    public static void placeholder(HudWidgetRenderer.Canvas canvas, Font font, @Nullable ViewReference ref,
                                   int width, int height){
        if(height < font.lineHeight) return;
        var label = StyledText.fit(font, ref == null ? "Camera unavailable" : "Waiting for camera", "regular", width);
        canvas.text(label, (width - font.width(label)) / 2.0, (height - font.lineHeight) / 2.0, 1, 0xFFB2C7D0);
    }

    // Request world-mounted feeds only while their depth-tested surfaces are visible
    public static boolean drawSurface(@Nullable ViewReference ref, int width, int height, float z,
                                      PoseStack stack, MultiBufferSource buffers){
        return !HologramRenderScope.isActive() && ref != null && width > 0 && height > 0
                && ViewSceneRenderer.drawVisibleFitted(ref, width, height, z, stack, buffers);
    }
}
