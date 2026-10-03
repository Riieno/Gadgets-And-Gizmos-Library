package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

// Keep GUI item depth within the layer owned by its surrounding controls
public final class LayeredItemRenderer{
    private LayeredItemRenderer(){
    }

    // Flush preceding controls and flatten the item's built-in GUI depth offset
    public static void render(GuiGraphics graphics, ItemStack stack, int x, int y){
        render(graphics, stack, x, y, -0.2F);
    }

    // Draw an icon above a self-contained modal or tablet panel background
    public static void renderVisible(GuiGraphics graphics, ItemStack stack, int x, int y){
        render(graphics, stack, x, y, 0.01F);
    }

    private static void render(GuiGraphics graphics, ItemStack stack, int x, int y, float depth){
        if(stack == null || stack.isEmpty()) return;
        graphics.flush();
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(0.0F, 0.0F, depth);
            graphics.pose().scale(1.0F, 1.0F, 0.001F);
            graphics.renderItem(stack, x, y);
        }finally{
            graphics.pose().popPose();
        }
    }
}
