package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import com.rieno.gadgetsandgizmos.lib.color.ColorPaint;

// Paint dropdown controls and option rows with the authored palette
public final class HudDropdownRenderer{
    private HudDropdownRenderer(){}

    public static HudControlRenderer.Rect control(HudControlRenderer.Canvas canvas, HudControlRenderer.Rect bounds,
            HudControlRenderer.Palette palette, HudStyleCatalog.DropdownStyle style, boolean open, boolean hovered){
        if(bounds.width() <= 0 || bounds.height() <= 0) return bounds;
        panel(canvas, bounds, palette, style, hovered);
        double size = Math.min(Math.min(5 * palette.scale(), bounds.height() / 4), bounds.width() / 8);
        double x = bounds.right() - Math.min(bounds.width() / 2, Math.max(8 * palette.scale(), bounds.height() / 2)), y = bounds.y() + bounds.height() / 2;
        var accent = canvas.paint("AccentColor", palette.accent());
        if(open) HudWidgetPaint.quad(canvas, x - size, y + size / 2, x + size, y + size / 2,
                x, y - size / 2, x - size, y + size / 2, accent, 30);
        else HudWidgetPaint.quad(canvas, x - size, y - size / 2, x, y + size / 2,
                x + size, y - size / 2, x - size, y - size / 2, accent, 30);
        double pad = Math.min(6 * palette.scale(), bounds.height() / 4);
        return new HudControlRenderer.Rect(bounds.x() + pad, bounds.y(), Math.max(0, x - size - bounds.x() - pad * 2), bounds.height());
    }

    public static void panel(HudControlRenderer.Canvas canvas, HudControlRenderer.Rect bounds,
            HudControlRenderer.Palette palette, HudStyleCatalog.DropdownStyle style, boolean hovered){
        if(style == HudStyleCatalog.DropdownStyle.NEUMORPHIC){
            HudControlRenderer.button(canvas, bounds, palette, HudStyleCatalog.ControlStyle.NEUMORPHIC_RAISED, false, hovered);
        }else if(style == HudStyleCatalog.DropdownStyle.MATERIAL){
            HudControlRenderer.button(canvas, bounds, palette, HudStyleCatalog.ControlStyle.MATERIAL_FILLED, false, hovered);
        }else{
            ColorPaint background = canvas.paint("BackgroundColor", palette.background());
            if(style == HudStyleCatalog.DropdownStyle.GLASSMORPHIC) background = ColorPaint.alpha(background, 0.65);
            HudControlRenderer.panel(canvas, bounds, background, canvas.paint("BorderColor", palette.border()), palette.borderWidth(), palette.radius());
            if(style == HudStyleCatalog.DropdownStyle.GLASSMORPHIC){
                double inset = Math.max(palette.borderWidth(), 1), h = Math.min(bounds.height() / 3, 7 * palette.scale());
                HudControlRenderer.panel((x1, y1, x2, y2, x3, y3, x4, y4, col, layer) ->
                        canvas.quad(x1, y1, x2, y2, x3, y3, x4, y4, col, layer + 4),
                        new HudControlRenderer.Rect(bounds.x() + inset, bounds.y() + inset,
                        Math.max(0, bounds.width() - inset * 2), h), 0x22FFFFFF, 0, 0, Math.max(0, palette.radius() - inset));
            }
        }
    }
}
