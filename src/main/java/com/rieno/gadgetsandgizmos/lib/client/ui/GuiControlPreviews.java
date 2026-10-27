package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// Keep preview frames, item icons and color insets in the same scaled coordinate space.
public final class GuiControlPreviews{
    private GuiControlPreviews(){}

    // Fit a label over a painted control without drawing another background
    public static void drawFittedText(GuiGraphics graphics, Font font, Component text, int x, int y,
                                      int width, int height, float maximumScale, int color, boolean centered){
        if(width <= 0 || height <= 0 || !Float.isFinite(maximumScale) || maximumScale <= 0) return;
        int textWidth = font.width(text);
        float scale = Math.min(maximumScale, Math.min(width / (float)Math.max(1, textWidth), height / (float)Math.max(1, font.lineHeight)));
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(centered ? x + (width - textWidth * scale) / 2 : x,
                    y + (height - font.lineHeight * scale) / 2, 1);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, 0, 0, color, true);
        }finally{ graphics.pose().popPose(); }
    }

    // Draw the same boolean switch in input and property rows
    public static void drawBooleanSwitch(GuiGraphics graphics, int right, int y, int rowHeight,
                                          boolean enabled, double scale){
        if(!Double.isFinite(scale) || scale <= 0) return;
        int width = Math.max(12, (int)Math.round(20 * scale));
        int height = Math.max(7, (int)Math.round(10 * scale));
        int left = right - width - Math.max(2, (int)Math.round(3 * scale));
        int top = y + Math.max(2, (rowHeight - height) / 2);
        int knobWidth = Math.max(4, width / 3);
        graphics.fill(left, top, left + width, top + height, enabled ? 0xFF4C9E70 : 0xFF394952);
        int knobX = enabled ? left + width - knobWidth - 2 : left + 2;
        graphics.fill(knobX, top + 1, knobX + knobWidth, top + height - 1,
                enabled ? 0xFFCAF4D8 : 0xFFB7C3C7);
    }

    // Draw an 18-unit slot with a 16-unit item, at the host canvas's scale.
    public static void drawItemSlot(GuiGraphics graphics, ItemStack stack, double x, double y,
                                    int color, boolean renderStack, double scale){
        if(!Double.isFinite(scale) || scale <= 0) return;
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale((float)scale, (float)scale, 1);
            graphics.fill(0, 0, 18, 18, (color & 0x00FFFFFF) | 0x55000000);
            if(renderStack && stack != null && !stack.isEmpty()) graphics.renderItem(stack, 1, 1);
            graphics.renderOutline(0, 0, 18, 18, color);
        }finally{
            graphics.pose().popPose();
        }
    }

    // Draw a 14-unit color preview, including its border and inner color, at canvas scale.
    public static void drawColorSwatch(GuiGraphics graphics, double x, double y, int color, double scale){
        if(!Double.isFinite(scale) || scale <= 0) return;
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale((float)scale, (float)scale, 1);
            graphics.fill(0, 1, 14, 13, 0xFF1D242B);
            graphics.fill(2, 3, 12, 11, color);
        }finally{
            graphics.pose().popPose();
        }
    }
}
