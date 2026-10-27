package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// Compact keyboard and book controls anchored to the bottom of a viewport.
public final class ViewportHelpButtons{
    public enum Action{ SHORTCUTS, DOCUMENTATION }

    private ViewportHelpButtons(){}

    public static Action hit(int left, int right, int bottom, double mouseX, double mouseY){
        return hit(left, right, bottom, mouseX, mouseY, true);
    }

    public static Action hit(int left, int right, int bottom, double mouseX, double mouseY, boolean documentation){
        for(var action : Action.values()){
            if(action == Action.DOCUMENTATION && !documentation) continue;
            if(bounds(left, right, bottom, action).contains(mouseX, mouseY)) return action;
        }
        return null;
    }

    public static void render(GuiGraphics graphics, Font font, int left, int right, int bottom, int mouseX, int mouseY,
                              HelpModalStyle style, Component shortcuts, Component documentation){
        render(graphics, font, left, right, bottom, mouseX, mouseY, style, shortcuts, documentation, true);
    }

    public static void render(GuiGraphics graphics, Font font, int left, int right, int bottom, int mouseX, int mouseY,
                              HelpModalStyle style, Component shortcuts, Component documentation, boolean available){
        for(var action : Action.values()){
            if(action == Action.DOCUMENTATION && !available) continue;
            var rect = bounds(left, right, bottom, action);
            if(rect.width() <= 0) continue;
            boolean hovered = rect.contains(mouseX, mouseY);
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? style.border() : style.panel());
            graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), style.border());
            int x = rect.x() + 3;
            int y = rect.y() + 5;
            int color = hovered ? style.accent() : style.text();
            if(action == Action.SHORTCUTS){
                graphics.renderOutline(x, y, 14, 10, color);
                for(int row = 0; row < 2; row++) for(int col = 0; col < 4; col++) graphics.fill(x + 2 + col * 3, y + 2 + row * 3, x + 3 + col * 3, y + 3 + row * 3, color);
                graphics.fill(x + 4, y + 7, x + 10, y + 8, color);
            }else{
                graphics.renderOutline(x, y - 1, 14, 12, color);
                graphics.fill(x + 6, y, x + 7, y + 10, color);
                for(int row = 0; row < 3; row++){
                    graphics.fill(x + 2, y + 1 + row * 3, x + 5, y + 2 + row * 3, color);
                    graphics.fill(x + 9, y + 1 + row * 3, x + 12, y + 2 + row * 3, color);
                }
            }
            if(hovered) graphics.renderTooltip(font, action == Action.SHORTCUTS ? shortcuts : documentation, mouseX, mouseY);
        }
    }

    private static HelpModalFrame.Rect bounds(int left, int right, int bottom, Action action){
        if(right - left < 52) return new HelpModalFrame.Rect(left, bottom, 0, 0);
        return new HelpModalFrame.Rect(left + 8 + action.ordinal() * 24, bottom - 28, 20, 20);
    }
}
