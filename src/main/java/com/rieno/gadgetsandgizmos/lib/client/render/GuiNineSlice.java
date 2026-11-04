package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

// Assemble nine atlas regions without sampling the gaps between them
public record GuiNineSlice(GuiTextureRegion topLeft, GuiTextureRegion top, GuiTextureRegion topRight,
                           GuiTextureRegion left, GuiTextureRegion center, GuiTextureRegion right,
                           GuiTextureRegion bottomLeft, GuiTextureRegion bottom, GuiTextureRegion bottomRight){
    public record Area(int x, int y, int width, int height){}

    // Draw the same atlas patches through GUI or projected-surface consumers
    @FunctionalInterface
    public interface Painter{
        void draw(GuiTextureRegion region, int x, int y, int width, int height);
    }
    public GuiNineSlice{
        for(GuiTextureRegion region : new GuiTextureRegion[]{topLeft, top, topRight, left, center, right,
                bottomLeft, bottom, bottomRight}) Objects.requireNonNull(region, "region");
        if(topLeft.width() != left.width() || left.width() != bottomLeft.width()
                || top.width() != center.width() || center.width() != bottom.width()
                || topRight.width() != right.width() || right.width() != bottomRight.width()
                || topLeft.height() != top.height() || top.height() != topRight.height()
                || left.height() != center.height() || center.height() != right.height()
                || bottomLeft.height() != bottom.height() || bottom.height() != bottomRight.height()){
            throw new IllegalArgumentException("Nine-slice regions must share column widths and row heights");
        }
    }

    // Keep the corner sizes while stretching the edges and center
    public void draw(GuiGraphics graphics, int x, int y, int width, int height){
        draw(graphics, x, y, width, height, 1.0D);
    }

    // Share the exact stretched centre bounds with fills and overlays
    public Area centerBounds(int x, int y, int width, int height){
        int left = leadingSize(width, topLeft.width(), topRight.width(), 1);
        int right = (int)Math.round(Math.min(width - left, topRight.width()));
        int top = leadingSize(height, topLeft.height(), bottomLeft.height(), 1);
        int bottom = (int)Math.round(Math.min(height - top, bottomLeft.height()));
        return new Area(x + left, y + top, Math.max(0, width - left - right), Math.max(0, height - top - bottom));
    }

    // Scale the corners with the host's layout and fit them inside smaller destinations
    public void draw(GuiGraphics graphics, int x, int y, int width, int height, double borderScale){
        paint((region, px, py, pw, ph) -> region.draw(graphics, px, py, pw, ph), x, y, width, height, borderScale);
    }

    public void paint(Painter painter, int x, int y, int width, int height, double borderScale){
        if(width <= 0 || height <= 0) return;
        if(!Double.isFinite(borderScale) || borderScale <= 0){
            throw new IllegalArgumentException("Nine-slice border scale must be finite and positive");
        }
        int leftWidth = leadingSize(width, topLeft.width(), topRight.width(), borderScale);
        int rightWidth = (int)Math.round(Math.min(width - leftWidth, topRight.width() * borderScale));
        int topHeight = leadingSize(height, topLeft.height(), bottomLeft.height(), borderScale);
        int bottomHeight = (int)Math.round(Math.min(height - topHeight, bottomLeft.height() * borderScale));
        int centerWidth = width - leftWidth - rightWidth;
        int centerHeight = height - topHeight - bottomHeight;
        int centerX = x + leftWidth;
        int centerY = y + topHeight;
        int rightX = centerX + centerWidth;
        int bottomY = centerY + centerHeight;

        painter.draw(topLeft, x, y, leftWidth, topHeight);
        painter.draw(top, centerX, y, centerWidth, topHeight);
        painter.draw(topRight, rightX, y, rightWidth, topHeight);
        painter.draw(left, x, centerY, leftWidth, centerHeight);
        painter.draw(center, centerX, centerY, centerWidth, centerHeight);
        painter.draw(right, rightX, centerY, rightWidth, centerHeight);
        painter.draw(bottomLeft, x, bottomY, leftWidth, bottomHeight);
        painter.draw(bottom, centerX, bottomY, centerWidth, bottomHeight);
        painter.draw(bottomRight, rightX, bottomY, rightWidth, bottomHeight);
    }

    // Reduce opposing corners proportionally when the available space is too small
    private static int leadingSize(int available, int leading, int trailing, double scale){
        double total = (double)leading + trailing;
        return (int)Math.round(total * scale <= available ? leading * scale : (double)available * leading / total);
    }
}
