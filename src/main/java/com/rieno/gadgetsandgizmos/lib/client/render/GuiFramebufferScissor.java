package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/** Clips logical GUI coordinates against an offscreen framebuffer. */
public final class GuiFramebufferScissor {
    private GuiFramebufferScissor() {
    }

    public record Bounds(int x, int y, int width, int height) {
    }

    public static Bounds bounds(int left, int top, int right, int bottom,
                                int guiWidth, int guiHeight, int framebufferWidth,
                                int framebufferHeight) {
        if (guiWidth <= 0 || guiHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0) {
            return new Bounds(0, 0, 0, 0);
        }
        double scaleX = (double) framebufferWidth / guiWidth;
        double scaleY = (double) framebufferHeight / guiHeight;
        int x1 = clamp((int) Math.floor(Math.min(left, right) * scaleX), framebufferWidth);
        int x2 = clamp((int) Math.ceil(Math.max(left, right) * scaleX), framebufferWidth);
        int y1 = clamp((int) Math.floor((guiHeight - Math.max(top, bottom)) * scaleY),
                framebufferHeight);
        int y2 = clamp((int) Math.ceil((guiHeight - Math.min(top, bottom)) * scaleY),
                framebufferHeight);
        return new Bounds(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    public static void enable(GuiGraphics graphics, int left, int top, int right, int bottom,
                              int guiWidth, int guiHeight, int framebufferWidth,
                              int framebufferHeight) {
        Bounds bounds = bounds(left, top, right, bottom,
                guiWidth, guiHeight, framebufferWidth, framebufferHeight);
        graphics.flush();
        RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    public static void disable(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.disableScissor();
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }
}
