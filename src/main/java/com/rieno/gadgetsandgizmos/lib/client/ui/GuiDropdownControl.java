package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.graph.render.GraphTextLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Locale;

// Draw one selected value and a separate arrow compartment inside a host's themed control
public final class GuiDropdownControl{
    private GuiDropdownControl(){}

    public record Colors(int arrowBackground, int divider, int value, int arrow){}
    public record Layout(int valueLeft, int valueRight, int arrowLeft, int arrowCenter, int textY){}

    // Match dropdown choices without changing their labels or original order
    public static List<String> filterOptions(List<String> options, String query){
        if(options == null || options.isEmpty()) return List.of();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if(normalized.isEmpty()) return List.copyOf(options);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).contains(normalized)).toList();
    }

    public static Layout layout(int x, int y, int width, int height, double scale){
        int padding = Math.max(1, (int)Math.round(3 * scale));
        int arrowWidth = Math.min(Math.max(1, width - padding), Math.max(10, (int)Math.round(15 * scale)));
        int arrowLeft = x + width - arrowWidth;
        return new Layout(x + padding, Math.max(x + padding, arrowLeft - padding), arrowLeft,
                arrowLeft + arrowWidth / 2, y + Math.max(1, (height - (int)Math.round(9 * scale)) / 2));
    }

    // The host draws the outer frame so this works with any theme and canvas zoom
    public static void drawContents(GuiGraphics graphics, Font font, String value, int x, int y, int width, int height,
                                    double scale, Colors colors){
        drawContents(graphics, font, value, x, y, width, height, scale, colors, false);
    }

    // Center labels inside their value compartment when matching a host's painted dropdown
    public static void drawContents(GuiGraphics graphics, Font font, String value, int x, int y, int width, int height,
                                    double scale, Colors colors, boolean centeredValue){
        if(width < 2 || height < 3 || !Double.isFinite(scale) || scale <= 0) return;
        Layout layout = layout(x, y, width, height, scale);
        int inset = Math.max(1, (int)Math.round(scale));
        graphics.fill(layout.arrowLeft(), y + inset, x + width - inset, y + height - inset, colors.arrowBackground());
        graphics.fill(layout.arrowLeft(), y + inset, layout.arrowLeft() + inset, y + height - inset, colors.divider());
        String shown = GraphTextLayout.ellipsize(value == null ? "" : value, font::width,
                Math.max(0, (int)Math.floor((layout.valueRight() - layout.valueLeft()) / scale)));
        graphics.pose().pushPose();
        try{
            double textX = centeredValue
                    ? layout.valueLeft() + (layout.valueRight() - layout.valueLeft() - font.width(shown) * scale) / 2
                    : layout.valueRight() - font.width(shown) * scale;
            graphics.pose().translate(textX, layout.textY(), 0);
            graphics.pose().scale((float)scale, (float)scale, 1);
            graphics.drawString(font, shown, 0, 0, colors.value(), false);
        }finally{ graphics.pose().popPose(); }
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(layout.arrowCenter(), layout.textY(), 0);
            graphics.pose().scale((float)scale, (float)scale, 1);
            graphics.drawString(font, "v", -font.width("v") / 2, 0, colors.arrow(), false);
        }finally{ graphics.pose().popPose(); }
    }
}
