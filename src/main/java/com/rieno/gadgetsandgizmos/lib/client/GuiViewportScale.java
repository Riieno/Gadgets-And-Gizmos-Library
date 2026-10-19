package com.rieno.gadgetsandgizmos.lib.client;

// Keep a temporary GUI scale within the width required by its controls
public final class GuiViewportScale{
    private GuiViewportScale(){}

    public static int forWidth(int preferred, int framebufferWidth, int minimumWidth){
        int scale = Math.max(1, preferred);
        if(minimumWidth <= 0) return scale;
        return Math.max(1, Math.min(scale, Math.max(0, framebufferWidth) / minimumWidth));
    }
}
