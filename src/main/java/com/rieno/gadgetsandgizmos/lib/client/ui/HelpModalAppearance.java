package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;

import java.util.Objects;

// Scale a complete painted panel and its layout with the same aspect ratio
public record HelpModalAppearance(GuiTextureRegion panel, int headerHeight, int footerHeight,
                                   int titleColor, int footerColor, float preferredScale){
    public HelpModalAppearance{
        Objects.requireNonNull(panel, "panel");
        if(headerHeight < 10 || footerHeight < 0 || headerHeight + footerHeight >= panel.height()){
            throw new IllegalArgumentException("Invalid help frame dimensions");
        }
        if(!Float.isFinite(preferredScale) || preferredScale <= 0) throw new IllegalArgumentException("Preferred scale must be positive");
    }

    public GuiCanvasTransform transform(int width, int height){
        return GuiCanvasTransform.fit(panel.width(), panel.height(), width, height, 8, preferredScale);
    }

    // Keep layout measurements in the original texture's coordinates
    public record Region(int x, int y, int width, int height){
        public Region{
            if(x < 0 || y < 0 || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid painted region");
        }
    }
}
