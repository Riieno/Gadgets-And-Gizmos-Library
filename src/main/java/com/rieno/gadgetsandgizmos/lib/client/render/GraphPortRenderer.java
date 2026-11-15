package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.gui.GuiGraphics;

// Draw graph port markers in graph units around a host's screen-space anchor
public final class GraphPortRenderer{
    private GraphPortRenderer(){}

    // Scale every marker layer together while retaining the host palette
    public static void data(GuiGraphics graphics, double x, double y, double zoom, int outer, int fill, int highlight){
        if(!Double.isFinite(zoom) || zoom <= 0) return;
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale((float)zoom, (float)zoom, 1);
            circle(graphics, 5, outer); circle(graphics, 4, fill); circle(graphics, 2, highlight);
        }finally{ graphics.pose().popPose(); }
    }

    // Keep circle coverage identical at each scale before applying the viewport transform
    private static void circle(GuiGraphics graphics, int radius, int col){
        for(int y = -radius; y <= radius; y++){
            int half = (int)Math.floor(Math.sqrt(Math.max(0, radius * radius - y * y)));
            graphics.fill(-half, y, half + 1, y + 1, col);
        }
    }
}
