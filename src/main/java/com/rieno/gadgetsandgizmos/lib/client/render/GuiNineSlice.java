package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

// Assemble nine atlas regions without sampling the gaps between them
public record GuiNineSlice(GuiTextureRegion topLeft, GuiTextureRegion top, GuiTextureRegion topRight,
                           GuiTextureRegion left, GuiTextureRegion center, GuiTextureRegion right,
                           GuiTextureRegion bottomLeft, GuiTextureRegion bottom, GuiTextureRegion bottomRight){
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

    // Scale the corners with the host's layout and fit them inside smaller destinations
    public void draw(GuiGraphics graphics, int x, int y, int width, int height, double borderScale){
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

        topLeft.draw(graphics, x, y, leftWidth, topHeight);
        top.draw(graphics, centerX, y, centerWidth, topHeight);
        topRight.draw(graphics, rightX, y, rightWidth, topHeight);
        left.draw(graphics, x, centerY, leftWidth, centerHeight);
        center.draw(graphics, centerX, centerY, centerWidth, centerHeight);
        right.draw(graphics, rightX, centerY, rightWidth, centerHeight);
        bottomLeft.draw(graphics, x, bottomY, leftWidth, bottomHeight);
        bottom.draw(graphics, centerX, bottomY, centerWidth, bottomHeight);
        bottomRight.draw(graphics, rightX, bottomY, rightWidth, bottomHeight);
    }

    // Reduce opposing corners proportionally when the available space is too small
    private static int leadingSize(int available, int leading, int trailing, double scale){
        double total = (double)leading + trailing;
        return (int)Math.round(total * scale <= available ? leading * scale : (double)available * leading / total);
    }
}
