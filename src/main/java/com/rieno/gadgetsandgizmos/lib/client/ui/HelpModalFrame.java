package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// Shared sizing and chrome for the two viewport help dialogs.
final class HelpModalFrame{
    private HelpModalFrame(){}

    static Rect bounds(int width, int height, int preferredWidth){
        int w = Math.max(1, Math.min(preferredWidth, width - 16));
        int h = Math.max(1, Math.min(520, height - 16));
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    static Rect bounds(int width, int height, int preferredWidth, HelpModalAppearance appearance){
        if(appearance == null) return bounds(width, height, preferredWidth);
        var transform = appearance.transform(width, height);
        return new Rect(transform.x(), transform.y(), Math.max(1, Math.round(appearance.panel().width() * transform.scale())),
                Math.max(1, Math.round(appearance.panel().height() * transform.scale())));
    }

    // Draw a full texture with one scale and no stretched or cropped sections
    static void texture(GuiGraphics graphics, GuiTextureRegion texture, Rect bounds){
        float scale = Math.min(bounds.width() / (float)texture.width(), bounds.height() / (float)texture.height());
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(bounds.x(), bounds.y(), 0);
            graphics.pose().scale(scale, scale, 1);
            texture.draw(graphics, 0, 0);
        }finally{ graphics.pose().popPose(); }
    }

    static Rect region(Rect bounds, HelpModalAppearance appearance, HelpModalAppearance.Region region){
        float scale = Math.min(bounds.width() / (float)appearance.panel().width(), bounds.height() / (float)appearance.panel().height());
        int x = bounds.x() + Math.round(region.x() * scale);
        int y = bounds.y() + Math.round(region.y() * scale);
        return new Rect(x, y, Math.max(1, Math.round(region.width() * scale)), Math.max(1, Math.round(region.height() * scale)));
    }

    // Fit wrapped text inside an existing painted control without changing its shape
    static void text(GuiGraphics graphics, Font font, Component text, Rect bounds, int color, boolean centered){
        float scale = 1;
        var lines = font.split(text, Math.max(1, bounds.width()));
        int lineHeight = font.lineHeight + 1;
        while(scale > 0.35F && lines.size() * lineHeight * scale > bounds.height()){
            scale = Math.max(0.35F, scale - 0.05F);
            lines = font.split(text, Math.max(1, (int)(bounds.width() / scale)));
        }
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(bounds.x(), bounds.y() + (bounds.height() - lines.size() * lineHeight * scale) / 2, 1);
            graphics.pose().scale(scale, scale, 1);
            for(int idx = 0; idx < lines.size(); idx++){
                var line = lines.get(idx);
                int x = centered ? Math.max(0, Math.round((bounds.width() / scale - font.width(line)) / 2)) : 0;
                graphics.drawString(font, line, x, idx * lineHeight, color, false);
            }
        }finally{ graphics.pose().popPose(); }
    }

    static void render(GuiGraphics graphics, Font font, int width, int height, Rect bounds,
                       Component title, HelpModalStyle style, double mouseX, double mouseY){
        render(graphics, font, width, height, bounds, title, style, mouseX, mouseY, null);
    }

    static void render(GuiGraphics graphics, Font font, int width, int height, Rect bounds,
                       Component title, HelpModalStyle style, double mouseX, double mouseY, HelpModalAppearance appearance){
        graphics.fill(0, 0, width, height, 0xB0000000);
        if(appearance == null){
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), style.background());
            graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), style.border());
            graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 27, style.panel());
        }else texture(graphics, appearance.panel(), bounds);
        int headerHeight = appearance == null ? 27 : region(bounds, appearance,
                new HelpModalAppearance.Region(0, 0, appearance.panel().width(), appearance.headerHeight())).height();
        int titleY = appearance == null ? 10 : Math.max(1, (headerHeight - font.lineHeight) / 2);
        int color = appearance == null ? style.text() : appearance.titleColor();
        Rect close = close(bounds, appearance);
        int titleWidth = appearance == null ? bounds.width() - 48 : close.x() - bounds.x() - 14;
        graphics.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(
                font.substrByWidth(title, Math.max(1, titleWidth))), bounds.x() + (appearance == null ? 10 : 5), bounds.y() + titleY, color, appearance != null);
        if(close.contains(mouseX, mouseY)) graphics.fill(close.x(), close.y(), close.right(), close.bottom(), appearance == null ? style.border() : 0x33FFFFFF);
        graphics.drawCenteredString(font, "\u00d7", close.x() + close.width() / 2,
                close.y() + Math.max(0, (close.height() - font.lineHeight) / 2), color);
    }

    static Rect close(Rect bounds){ return new Rect(bounds.right() - 25, bounds.y() + 4, 20, 20); }

    static Rect close(Rect bounds, HelpModalAppearance appearance){
        if(appearance == null) return close(bounds);
        int size = Math.max(1, appearance.headerHeight() - 2);
        return region(bounds, appearance, new HelpModalAppearance.Region(appearance.panel().width() - size - 2, 1, size, size));
    }

    static void scrollbar(GuiGraphics graphics, Rect bounds, ListScrollState scroll, HelpModalStyle style){
        if(scroll.maximumOffset() <= 0) return;
        graphics.fill(bounds.right() - 4, bounds.y(), bounds.right() - 1, bounds.bottom(), style.panel());
        int y = bounds.y() + (int)Math.round((bounds.height() - 16) * scroll.fraction());
        graphics.fill(bounds.right() - 4, y, bounds.right() - 1, y + 16, style.accent());
    }

    record Rect(int x, int y, int width, int height){
        int right(){ return x + width; }
        int bottom(){ return y + height; }
        boolean contains(double x, double y){ return x >= this.x && x < right() && y >= this.y && y < bottom(); }
    }
}
