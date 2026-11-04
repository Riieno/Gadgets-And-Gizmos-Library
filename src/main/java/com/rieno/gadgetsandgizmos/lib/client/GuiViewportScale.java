package com.rieno.gadgetsandgizmos.lib.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.ClientHooks;

// Keep a temporary GUI scale within the width required by its controls
public final class GuiViewportScale{
    private GuiViewportScale(){}

    // Resize GUI coordinates without replacing world color and depth attachments
    public static boolean apply(int preferred){
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        int scale = window.calculateScale(preferred, mc.isEnforceUnicode());
        if(window.getGuiScale() == scale) return false;
        window.setGuiScale(scale);
        if(mc.screen != null){
            mc.screen.resize(mc, window.getGuiScaledWidth(), window.getGuiScaledHeight());
            ClientHooks.resizeGuiLayers(mc, window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
        mc.mouseHandler.setIgnoreFirstMove();
        return true;
    }

    public static int forWidth(int preferred, int framebufferWidth, int minimumWidth){
        int scale = Math.max(1, preferred);
        if(minimumWidth <= 0) return scale;
        return Math.max(1, Math.min(scale, Math.max(0, framebufferWidth) / minimumWidth));
    }
}
