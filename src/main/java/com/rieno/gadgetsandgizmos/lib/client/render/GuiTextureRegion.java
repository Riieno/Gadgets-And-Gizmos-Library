package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

// Draw one cropped region from a GUI texture atlas
public record GuiTextureRegion(ResourceLocation texture, int textureWidth, int textureHeight,
                               int u, int v, int width, int height){
    public GuiTextureRegion{
        Objects.requireNonNull(texture, "texture");
        if(textureWidth <= 0 || textureHeight <= 0 || u < 0 || v < 0
                || width <= 0 || height <= 0 || u > textureWidth - width || v > textureHeight - height){
            throw new IllegalArgumentException("GUI region must fit inside its texture atlas");
        }
    }

    // Draw at the region's original size
    public void draw(GuiGraphics graphics, int x, int y){
        draw(graphics, x, y, width, height);
    }

    // Scale the cropped region to the requested size
    public void draw(GuiGraphics graphics, int x, int y, int drawWidth, int drawHeight){
        if(drawWidth <= 0 || drawHeight <= 0) return;
        graphics.blit(texture, x, y, drawWidth, drawHeight,
                (float)u, (float)v, width, height, textureWidth, textureHeight);
    }

    // Fit the complete region with one scale for both axes
    public void drawFitted(GuiGraphics graphics, int x, int y, int drawWidth, int drawHeight){
        if(drawWidth <= 0 || drawHeight <= 0) return;
        float scale = Math.min(drawWidth / (float)width, drawHeight / (float)height);
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(x + (drawWidth - width * scale) / 2, y + (drawHeight - height * scale) / 2, 0);
            graphics.pose().scale(scale, scale, 1);
            draw(graphics, 0, 0);
        }finally{ graphics.pose().popPose(); }
    }

    // Keep the end caps intact and repeat the middle at the same scale as the height
    public void drawHorizontallyTiled(GuiGraphics graphics, int x, int y, int drawWidth, int drawHeight,
                                      int leftWidth, int rightWidth){
        if(leftWidth < 0 || rightWidth < 0 || leftWidth + rightWidth >= width){
            throw new IllegalArgumentException("GUI end caps must leave a repeatable middle");
        }
        if(drawWidth <= 0 || drawHeight <= 0) return;
        float scale = drawHeight / (float)height;
        if(drawWidth < (leftWidth + rightWidth) * scale){
            throw new IllegalArgumentException("GUI width must fit both end caps");
        }
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1);
            int total = Math.round(drawWidth / scale);
            drawStrip(graphics, 0, 0, leftWidth);
            int middle = width - leftWidth - rightWidth;
            for(int pos = leftWidth; pos < total - rightWidth; pos += middle){
                drawStrip(graphics, pos, leftWidth, Math.min(middle, total - rightWidth - pos));
            }
            drawStrip(graphics, total - rightWidth, width - rightWidth, rightWidth);
        }finally{ graphics.pose().popPose(); }
    }

    // Repeat a region at the requested tile size and crop the final row and column
    public void drawTiled(GuiGraphics graphics, int x, int y, int drawWidth, int drawHeight,
                          int tileWidth, int tileHeight){
        if(tileWidth <= 0 || tileHeight <= 0) throw new IllegalArgumentException("GUI tile dimensions must be positive");
        if(drawWidth <= 0 || drawHeight <= 0) return;
        try(var clip = GuiPoseScissor.push(graphics, x, y, x + drawWidth, y + drawHeight)){
            for(int row = 0; row < drawHeight; row += tileHeight){
                for(int col = 0; col < drawWidth; col += tileWidth){
                    draw(graphics, x + col, y + row, tileWidth, tileHeight);
                }
            }
        }
    }

    private void drawStrip(GuiGraphics graphics, int x, int sourceX, int stripWidth){
        if(stripWidth <= 0) return;
        graphics.blit(texture, x, 0, stripWidth, height, (float)(u + sourceX), (float)v,
                stripWidth, height, textureWidth, textureHeight);
    }
}
