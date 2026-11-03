package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// Shared drawing and hit bounds for an editable graph group title and color button.
public final class GraphGroupHeader{
    private GraphGroupHeader(){}

    public static Layout layout(int x, int y, int width, double scale, int titleWidth){
        double zoom = Double.isFinite(scale) && scale > 0 ? scale : 1;
        int height = Math.max(1, (int)Math.round(18 * zoom));
        int inset = Math.max(1, (int)Math.round(5 * zoom));
        int buttonSize = Math.min(Math.max(1, width), Math.max(1, (int)Math.round(14 * zoom)));
        int buttonX = x + width - buttonSize - Math.max(1, (int)Math.round(2 * zoom));
        int titleX = x + inset;
        int available = Math.max(0, buttonX - titleX - Math.max(1, (int)Math.round(3 * zoom)));
        int clickable = Math.min(available, Math.max((int)Math.round(32 * zoom),
                (int)Math.ceil((titleWidth + 4) * zoom)));
        return new Layout(titleX, y + inset, available, clickable, y, height,
                buttonX, y + Math.max(0, (height - buttonSize) / 2), buttonSize, (float)zoom);
    }

    public static void render(GuiGraphics graphics, Font font, Layout bounds, String title,
                              boolean editing, int mouseX, int mouseY){
        if(!editing && bounds.titleWidth() > 0){
            String visible = font.plainSubstrByWidth(title, (int)(bounds.titleWidth() / bounds.scale()));
            graphics.pose().pushPose();
            graphics.pose().translate(bounds.titleX(), bounds.titleY(), 0);
            graphics.pose().scale(bounds.scale(), bounds.scale(), 1);
            graphics.drawString(font, visible, 0, 0, 0xFFFFFFFF, false);
            graphics.pose().popPose();
        }
        ColorPickerModal.renderButton(graphics, bounds.buttonX(), bounds.buttonY(), bounds.buttonSize(), bounds.colorContains(mouseX, mouseY));
    }

    public record Layout(int titleX, int titleY, int titleWidth, int titleHitWidth,
                         int headerY, int headerHeight, int buttonX, int buttonY,
                         int buttonSize, float scale){
        public boolean titleContains(double mouseX, double mouseY){
            return mouseX >= titleX && mouseX < titleX + titleHitWidth
                    && mouseY >= headerY && mouseY < headerY + headerHeight;
        }

        public boolean colorContains(double mouseX, double mouseY){
            return mouseX >= buttonX && mouseX < buttonX + buttonSize
                    && mouseY >= buttonY && mouseY < buttonY + buttonSize;
        }
    }
}
