package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// Draw large virtual inventory totals inside a sixteen-pixel item slot
public final class ItemCountRenderer{
    private ItemCountRenderer(){}

    public static String label(long count){
        if(count <= 10000) return Long.toString(count);
        return count / 1000 + "k";
    }

    public static void render(GuiGraphics graphics, Font font, long count, int x, int y){
        if(count <= 1) return;
        String text = label(count);
        float scale = Math.min(.75F, 16F / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        try{
            // Match vanilla count decorations above native slot item models
            graphics.pose().translate(x + 16, y + 16, 300);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, -font.width(text), -font.lineHeight, 0xFFFFFFFF, true);
        }finally{ graphics.pose().popPose(); }
    }
}
