package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Fit the native CC character cells without stretching the terminal or replacing its font
public final class TerminalPreview{
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("computercraft", "textures/gui/term_font.png");
    private TerminalPreview(){}

    public static void render(GuiGraphics graphics, ScriptTerminal terminal, int x, int y, int width, int height){
        graphics.fill(x, y, x + width, y + height, 0xFF080808);
        var fit = com.rieno.gadgetsandgizmos.lib.display.SurfaceFit.fit(terminal.width() * 6, terminal.height() * 9, width, height, true);
        try(var clip = GuiPoseScissor.push(graphics, x, y, x + width, y + height)){
            graphics.pose().pushPose();
            try{
                graphics.pose().translate(x + fit.x(), y + fit.y(), 0);
                graphics.pose().scale((float)fit.scaleX(), (float)fit.scaleY(), 1);
                for(int row = 0; row < terminal.height(); row++) for(int col = 0; col < terminal.width(); col++){
                    int background = terminal.palette().get(Character.digit(terminal.background().get(row).charAt(col), 16)) | 0xFF000000;
                    graphics.fill(col * 6, row * 9, col * 6 + 6, row * 9 + 9, background);
                }
                graphics.flush();
                for(int row = 0; row < terminal.height(); row++) for(int col = 0; col < terminal.width(); col++){
                    int ch = terminal.text().get(row).charAt(col);
                    if(ch == 0 || ch == 32) continue;
                    if(ch > 255) ch = '?';
                    int color = terminal.palette().get(Character.digit(terminal.foreground().get(row).charAt(col), 16));
                    graphics.setColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1);
                    graphics.blit(FONT, col * 6, row * 9, 1 + ch % 16 * 8, 1 + ch / 16 * 11, 6, 9, 256, 256);
                }
                graphics.setColor(1, 1, 1, 1);
                if(terminal.blink() && System.currentTimeMillis() / 400 % 2 == 0 && terminal.cursorX() >= 0 && terminal.cursorX() < terminal.width()
                        && terminal.cursorY() >= 0 && terminal.cursorY() < terminal.height()){
                    int x0 = terminal.cursorX() * 6, y0 = terminal.cursorY() * 9 + 7;
                    graphics.fill(x0, y0, x0 + 6, y0 + 2, terminal.palette().get(terminal.cursorColor()) | 0xFF000000);
                }
            }finally{ graphics.setColor(1, 1, 1, 1); graphics.pose().popPose(); }
        }
    }
}
