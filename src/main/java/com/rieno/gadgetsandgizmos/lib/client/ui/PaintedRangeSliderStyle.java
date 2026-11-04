package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

// Draw a selected interval between two thumbs on a host-painted rail
public record PaintedRangeSliderStyle(GuiTextureRegion fill, GuiTextureRegion lowerThumb,
                                      GuiTextureRegion upperThumb, Axis axis, boolean reversed){
    public enum Axis{ HORIZONTAL, VERTICAL }

    public PaintedRangeSliderStyle{
        Objects.requireNonNull(fill, "fill");
        Objects.requireNonNull(lowerThumb, "lowerThumb");
        Objects.requireNonNull(upperThumb, "upperThumb");
        Objects.requireNonNull(axis, "axis");
        if(lowerThumb.width() != upperThumb.width() || lowerThumb.height() != upperThumb.height()){
            throw new IllegalArgumentException("Range slider thumbs must share their dimensions");
        }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Convert pointer position using the same centers and travel as the painted thumbs
    public double fractionAt(double mouseX, double mouseY, int x, int y, int width, int height){
        double pos = axis == Axis.VERTICAL ? mouseY - y : mouseX - x;
        double fraction = Math.clamp((pos - thumbLength() / 2.0) / travel(width, height), 0, 1);
        return reversed ? 1 - fraction : fraction;
    }

    // Include the thumb's overhang beside the painted rail
    public boolean contains(double mouseX, double mouseY, int x, int y, int width, int height){
        int left = axis == Axis.VERTICAL ? x + (width - lowerThumb.width()) / 2 : x;
        int top = axis == Axis.HORIZONTAL ? y + (height - lowerThumb.height()) / 2 : y;
        int drawWidth = axis == Axis.VERTICAL ? lowerThumb.width() : width;
        int drawHeight = axis == Axis.HORIZONTAL ? lowerThumb.height() : height;
        return inside(mouseX, mouseY, left, top, drawWidth, drawHeight);
    }

    public boolean isOverThumb(double mouseX, double mouseY, int x, int y, int width, int height, double fraction){
        return inside(mouseX, mouseY, thumbX(x, width, height, fraction), thumbY(y, width, height, fraction),
                lowerThumb.width(), lowerThumb.height());
    }

    // Crop the fill to the selected interval and paint the active thumb last
    public void draw(GuiGraphics graphics, int x, int y, int width, int height, DualThumbSlider slider,
                     DualThumbSlider.Thumb highlighted){
        if(width <= 0 || height <= 0) return;
        int lowerPos = position(width, height, slider.lowerFraction()) + thumbLength() / 2;
        int upperPos = position(width, height, slider.upperFraction()) + thumbLength() / 2;
        int start = Math.min(lowerPos, upperPos);
        int span = Math.abs(upperPos - lowerPos);
        if(span > 0){
            int length = axis == Axis.VERTICAL ? height : width;
            int sourceLength = axis == Axis.VERTICAL ? fill.height() : fill.width();
            int sourceStart = Math.clamp((int)Math.round(start * sourceLength / (double)length), 0, sourceLength - 1);
            int sourceSpan = Math.clamp((int)Math.round(span * sourceLength / (double)length), 1, sourceLength - sourceStart);
            graphics.blit(fill.texture(), x + (axis == Axis.HORIZONTAL ? start : 0),
                    y + (axis == Axis.VERTICAL ? start : 0), axis == Axis.VERTICAL ? width : span,
                    axis == Axis.VERTICAL ? span : height,
                    (float)(fill.u() + (axis == Axis.HORIZONTAL ? sourceStart : 0)),
                    (float)(fill.v() + (axis == Axis.VERTICAL ? sourceStart : 0)),
                    axis == Axis.VERTICAL ? fill.width() : sourceSpan,
                    axis == Axis.VERTICAL ? sourceSpan : fill.height(), fill.textureWidth(), fill.textureHeight());
        }
        boolean lowerLast = highlighted == DualThumbSlider.Thumb.LOWER;
        drawThumb(graphics, x, y, width, height, slider, !lowerLast, highlighted);
        drawThumb(graphics, x, y, width, height, slider, lowerLast, highlighted);
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private void drawThumb(GuiGraphics graphics, int x, int y, int width, int height, DualThumbSlider slider,
                           boolean lower, DualThumbSlider.Thumb highlighted){
        GuiTextureRegion thumb = lower ? lowerThumb : upperThumb;
        double fraction = lower ? slider.lowerFraction() : slider.upperFraction();
        int left = thumbX(x, width, height, fraction);
        int top = thumbY(y, width, height, fraction);
        thumb.draw(graphics, left, top);
        if(highlighted == (lower ? DualThumbSlider.Thumb.LOWER : DualThumbSlider.Thumb.UPPER)){
            graphics.fill(left + 1, top + 1, left + thumb.width() - 1, top + thumb.height() - 1, 0x33FFFFFF);
        }
    }

    private int thumbX(int x, int width, int height, double fraction){
        return x + (axis == Axis.VERTICAL ? (width - lowerThumb.width()) / 2 : position(width, height, fraction));
    }
    private int thumbY(int y, int width, int height, double fraction){
        return y + (axis == Axis.HORIZONTAL ? (height - lowerThumb.height()) / 2 : position(width, height, fraction));
    }
    private int position(int width, int height, double fraction){
        return (int)Math.round((reversed ? 1 - Math.clamp(fraction, 0, 1) : Math.clamp(fraction, 0, 1)) * travel(width, height));
    }
    private int thumbLength(){ return axis == Axis.VERTICAL ? lowerThumb.height() : lowerThumb.width(); }
    private int travel(int width, int height){ return Math.max(1, (axis == Axis.VERTICAL ? height : width) - thumbLength()); }
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height){
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
