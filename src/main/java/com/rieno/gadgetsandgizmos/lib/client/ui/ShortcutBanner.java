package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

// Wrap shortcut hints inside a reserved strip at the bottom of a viewport
public final class ShortcutBanner{
    private ShortcutBanner(){}

    public record Shortcut(String keys, Component action){}
    public record Layout(List<FormattedCharSequence> lines, float scale, int height){
        public Layout{ lines = List.copyOf(lines); }
    }

    // Keep every shortcut visible as the viewport narrows
    public static Layout layout(Font font, List<Shortcut> shortcuts, int width, float scale, int keyColor, int textColor){
        scale = Float.isFinite(scale) && scale > 0 ? scale : 1.0F;
        var text = Component.empty();
        for(Shortcut shortcut : shortcuts){
            if(!text.getSiblings().isEmpty()) text.append(Component.literal("   ·   ").withColor(textColor));
            text.append(Component.literal(shortcut.keys() + " ").withColor(keyColor));
            text.append(shortcut.action().copy().withColor(textColor));
        }
        var lines = font.split(text, Math.max(1, (int)((width - 12) / scale)));
        return new Layout(lines, scale, shortcuts.isEmpty() ? 0 : (int)Math.ceil(lines.size() * (font.lineHeight + 2) * scale) + 8);
    }

    public static void render(GuiGraphics graphics, Font font, Layout layout,
            int x, int bottom, int width, int background, int border){
        if(width <= 0 || layout.height() == 0) return;
        int top = bottom - layout.height();
        graphics.fill(x, top, x + width, bottom, background);
        graphics.fill(x, top, x + width, top + 1, border);
        graphics.enableScissor(x, top, x + width, bottom);
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(x + 6, top + 4, 0);
            graphics.pose().scale(layout.scale(), layout.scale(), 1);
            for(int idx = 0; idx < layout.lines().size(); idx++){
                graphics.drawString(font, layout.lines().get(idx), 0, idx * (font.lineHeight + 2), 0xFFFFFFFF, false);
            }
        }finally{
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }
}
