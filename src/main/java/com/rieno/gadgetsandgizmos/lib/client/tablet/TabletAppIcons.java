package com.rieno.gadgetsandgizmos.lib.client.tablet;

import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

// Allow code-rendered app icons alongside the existing texture icons
public final class TabletAppIcons{
    private static final Map<ResourceLocation, Renderer> RENDERERS = new HashMap<>();
    private TabletAppIcons(){}

    public static void register(ResourceLocation appId, Renderer renderer){
        if(RENDERERS.putIfAbsent(appId, renderer) != null) throw new IllegalStateException("App icon already registered: " + appId);
    }

    public static void render(TabletAppDefinition app, GuiGraphics graphics, int x, int y, int size){
        Renderer renderer = RENDERERS.get(app.id());
        if(renderer == null) graphics.blit(app.icon(), x, y, size, size, 0, 0, 64, 64, 64, 64);
        else renderer.render(graphics, x, y, size);
    }

    @FunctionalInterface
    public interface Renderer{ void render(GuiGraphics graphics, int x, int y, int size); }
}
