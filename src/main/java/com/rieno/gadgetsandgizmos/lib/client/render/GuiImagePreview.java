package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

// Fit loaded or resource images to a preview without stretching their pixels
public final class GuiImagePreview{
    private final Map<ResourceLocation, GuiTextureRegion> resources = new ConcurrentHashMap<>();

    public boolean draw(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height){
        if(texture == null || width <= 0 || height <= 0) return false;
        var mc = Minecraft.getInstance();
        GuiTextureRegion region;
        if(mc.getTextureManager().getTexture(texture) instanceof DynamicTexture dynamic && dynamic.getPixels() != null){
            var pixels = dynamic.getPixels();
            region = new GuiTextureRegion(texture, pixels.getWidth(), pixels.getHeight(), 0, 0, pixels.getWidth(), pixels.getHeight());
        }else{
            region = resources.computeIfAbsent(texture, key -> {
                var resource = mc.getResourceManager().getResource(key);
                if(resource.isEmpty()) return null;
                try(var stream = resource.get().open(); var pixels = NativeImage.read(stream)){
                    return new GuiTextureRegion(key, pixels.getWidth(), pixels.getHeight(), 0, 0, pixels.getWidth(), pixels.getHeight());
                }catch(java.io.IOException ex){ return null; }
            });
        }
        if(region == null) return false;
        region.drawFitted(graphics, x, y, width, height); return true;
    }

    // Clear resource dimensions when the host reloads its assets
    public void clear(){ resources.clear(); }
}
