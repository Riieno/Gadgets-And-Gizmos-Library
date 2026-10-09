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
}
