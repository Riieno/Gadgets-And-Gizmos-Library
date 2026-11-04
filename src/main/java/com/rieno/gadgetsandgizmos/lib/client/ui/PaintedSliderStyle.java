package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

// Paint a cropped fill and a capped thumb over a screen's existing slider rail
public record PaintedSliderStyle(GuiTextureRegion fill, GuiTextureRegion thumb, int thumbWidth){
    public PaintedSliderStyle{
        Objects.requireNonNull(fill);
        Objects.requireNonNull(thumb);
        if(thumbWidth < 2 || thumb.width() < 3) throw new IllegalArgumentException("Slider thumb must retain both edges and a middle");
    }

    public int thumbLeft(int x, int width, double fraction){
        return x + (int)Math.round(Math.clamp(fraction, 0, 1) * Math.max(0, width - thumbWidth));
    }

    // Use the same thumb travel for pointer input and drawing
    public double fractionAt(double mouseX, int x, int width){
        return Math.clamp((mouseX - x - thumbWidth / 2.0) / Math.max(1, width - thumbWidth), 0, 1);
    }

    public void draw(GuiGraphics graphics, int x, int y, int width, int height, double fraction, boolean highlighted){
        if(width <= 0 || height <= 0) return;
        int left = thumbLeft(x, width, fraction);
        int filled = Math.min(width, left - x + thumbWidth / 2);
        if(filled > 0){
            int sourceWidth = Math.max(1, (int)Math.round(fill.width() * filled / (double)width));
            graphics.blit(fill.texture(), x, y + (height - fill.height()) / 2, filled, fill.height(),
                    (float)fill.u(), (float)fill.v(), sourceWidth, fill.height(), fill.textureWidth(), fill.textureHeight());
        }
        int top = y + (height - thumb.height()) / 2;
        thumb.drawHorizontallyTiled(graphics, left, top, thumbWidth, thumb.height(), 1, 1);
        if(highlighted) graphics.fill(left + 1, top + 1, left + thumbWidth - 1, top + thumb.height() - 1, 0x33FFFFFF);
    }
}
