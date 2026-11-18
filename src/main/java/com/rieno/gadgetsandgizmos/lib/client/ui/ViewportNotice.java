package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

// Keep persistent notices readable without following the viewport's pan or zoom
public final class ViewportNotice{
    private ViewportNotice(){}

    public record Layout(List<FormattedCharSequence> lines, int width, int height){
        public Layout{ lines = List.copyOf(lines); }
    }

    // Wrap each message separately and retain the complete warning text
    public static Layout layout(Font font, List<Component> messages, int width){
        if(messages.isEmpty() || width <= 16) return new Layout(List.of(), 0, 0);
        int measured = messages.stream().mapToInt(font::width).max().orElse(0);
        int fitted = Math.min(width, measured + 16);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for(Component message : messages) lines.addAll(font.split(message, Math.max(1, fitted - 16)));
        return new Layout(lines, fitted, lines.size() * (font.lineHeight + 2) + 10);
    }

    public static void render(GuiGraphics graphics, Font font, Layout layout, int x, int y,
                              int background, int accent, int text){
        if(layout.lines().isEmpty()) return;
        graphics.fill(x, y, x + layout.width(), y + layout.height(), background);
        graphics.renderOutline(x, y, layout.width(), layout.height(), accent);
        graphics.fill(x, y, x + 3, y + layout.height(), accent);
        for(int idx = 0; idx < layout.lines().size(); idx++){
            graphics.drawString(font, layout.lines().get(idx), x + 8, y + 5 + idx * (font.lineHeight + 2), text, false);
        }
    }
}
