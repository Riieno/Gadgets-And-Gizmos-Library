package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Objects;

// Share painted frames, fields and controls while the host supplies artwork and layout
public record PaintedGuiStyle(GuiNineSlice panel, GuiNineSlice field, GuiNineSlice button,
                              GuiNineSlice selectedButton, GuiTextureRegion separator,
                              int textColor, int mutedColor){
    public PaintedGuiStyle{
        Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(selectedButton, "selectedButton");
        Objects.requireNonNull(separator, "separator");
    }

    // Split one contiguous painted region without scaling its border pixels
    public static GuiNineSlice slice(GuiTextureRegion region, int left, int top, int right, int bottom){
        if(left <= 0 || top <= 0 || right <= 0 || bottom <= 0
                || left + right >= region.width() || top + bottom >= region.height()){
            throw new IllegalArgumentException("Painted borders must leave a center region");
        }
        int[] widths = {left, region.width() - left - right, right};
        int[] heights = {top, region.height() - top - bottom, bottom};
        GuiTextureRegion[] parts = new GuiTextureRegion[9];
        int y = region.v();
        for(int row = 0; row < 3; row++){
            int x = region.u();
            for(int col = 0; col < 3; col++){
                parts[row * 3 + col] = new GuiTextureRegion(region.texture(), region.textureWidth(),
                        region.textureHeight(), x, y, widths[col], heights[row]);
                x += widths[col];
            }
            y += heights[row];
        }
        return new GuiNineSlice(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6], parts[7], parts[8]);
    }

    // Draw a framed modal with the painted title and footer intact
    public void renderPanel(GuiGraphics graphics, int x, int y, int width, int height){
        panel.draw(graphics, x, y, width, height);
    }

    // Draw a recessed value or list section
    public void renderInset(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered, boolean selected){
        field.draw(graphics, x, y, width, height);
        if(selected) graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x22E5BA64);
        if(hovered) graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x11FFFFFF);
    }

    // Draw a control background with a separate disabled state
    public void renderButton(GuiGraphics graphics, int x, int y, int width, int height,
                             boolean hovered, boolean enabled, boolean selected){
        (selected ? selectedButton : button).draw(graphics, x, y, width, height);
        if(!enabled) graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x66000000);
        else if(hovered) graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x22FFFFFF);
    }

    // Fit control labels inside their painted bounds
    public void renderTextButton(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                 Component label, boolean hovered, boolean enabled, boolean selected){
        renderButton(graphics, x, y, width, height, hovered, enabled, selected);
        GuiControlPreviews.drawFittedText(graphics, font, label, x + 3, y + 1, Math.max(1, width - 6),
                Math.max(1, height - 2), 1, enabled ? textColor : mutedColor, true);
    }

    // Separate neighboring sections with the host's painted rule
    public void renderSeparator(GuiGraphics graphics, int x, int y, int width){
        separator.draw(graphics, x, y, width, separator.height());
    }
}
