package com.rieno.gadgetsandgizmos.lib.client.tablet;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import com.rieno.gadgetsandgizmos.lib.tablet.ControlCardStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Share the painted canvas, card frames and action partials from a control-card atlas
public final class PaintedCardArtwork{
    private final ResourceLocation atlas;
    private final GuiNineSlice single, wide, tall, button;

    public PaintedCardArtwork(ResourceLocation atlas){
        this.atlas = atlas;
        single = PaintedGuiStyle.slice(region(32, 264, 72, 66), 3, 3, 3, 3);
        wide = PaintedGuiStyle.slice(region(117, 334, 144, 66), 3, 3, 3, 3);
        tall = PaintedGuiStyle.slice(region(32, 334, 72, 66), 3, 3, 3, 3);
        button = PaintedGuiStyle.slice(region(217, 319, 44, 11), 2, 2, 2, 2);
    }

    public void background(GuiGraphics graphics){ region(0, 0, 320, 180).draw(graphics, 0, 0); }

    public void frame(GuiGraphics graphics, ControlCardStyle.Size size, int color, int x, int y, int width, int height){
        int max = Math.max(48, Math.max(color >> 16 & 255, Math.max(color >> 8 & 255, color & 255)));
        graphics.setColor((color >> 16 & 255) / (float)max, (color >> 8 & 255) / (float)max, (color & 255) / (float)max, 1);
        try{ (size == ControlCardStyle.Size.WIDE ? wide : size == ControlCardStyle.Size.TALL ? tall : single).draw(graphics, x, y, width, height); }
        finally{ graphics.setColor(1, 1, 1, 1); }
    }

    public void button(GuiGraphics graphics, int x, int y, int width, int height){ button.draw(graphics, x, y, width, height); }
    // Tint the painted button itself so its borders and alpha outline stay aligned
    public void button(GuiGraphics graphics, int x, int y, int width, int height, int color){
        int max = Math.max(48, Math.max(color >> 16 & 255, Math.max(color >> 8 & 255, color & 255)));
        graphics.setColor((color >> 16 & 255) / (float)max, (color >> 8 & 255) / (float)max, (color & 255) / (float)max, 1);
        try{ button.draw(graphics, x, y, width, height); }
        finally{ graphics.setColor(1, 1, 1, 1); }
    }
    public void addIcon(GuiGraphics graphics, int x, int y, int width, int height){ region(268, 367, 31, 33).draw(graphics, x, y, width, height); }
    public void editIcon(GuiGraphics graphics, int x, int y, int size){ region(283, 319, 10, 10).draw(graphics, x, y, size, size); }
    public void deleteIcon(GuiGraphics graphics, int x, int y, int size){ region(283, 335, 10, 10).draw(graphics, x, y, size, size); }

    private GuiTextureRegion region(int x, int y, int width, int height){ return new GuiTextureRegion(atlas, 320, 400, x, y, width, height); }
}
