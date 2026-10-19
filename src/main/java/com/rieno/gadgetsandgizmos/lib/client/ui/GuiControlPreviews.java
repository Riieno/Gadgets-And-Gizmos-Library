package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

// Keep preview frames, item icons and color insets in the same scaled coordinate space.
public final class GuiControlPreviews{
    private GuiControlPreviews(){}

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
