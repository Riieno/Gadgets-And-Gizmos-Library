package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftTerminalColors;
import com.rieno.gadgetsandgizmos.lib.script.TerminalColors;
import dan200.computercraft.core.terminal.Terminal;

// Share exact cell geometry between CC's screen and monitor vertex emitters
public final class ComputerCraftTerminalRenderer{
    @FunctionalInterface
    public interface Quad{
        void draw(float x1, float y1, float x2, float y2, int argb, float u1, float v1, float u2, float v2);
    }
    private ComputerCraftTerminalRenderer(){}

    public static boolean active(Terminal terminal){ return ComputerCraftTerminalColors.colors(terminal).hasColors(); }

    public static void foreground(Terminal terminal, float x, float y, Quad out){
        for(int row = 0; row < terminal.getHeight(); row++){
            for(int col = 0; col < terminal.getWidth(); col++){
                glyph(out, x + col * 6, y + row * 9, terminal.getLine(row).charAt(col), color(terminal, col, row, true));
            }
        }
    }
    public static void background(Terminal terminal, float x, float y, float top, float bottom, float left, float right, Quad out){
        for(int row = 0; row < terminal.getHeight(); row++){
            for(int col = 0; col < terminal.getWidth(); col++){
                out.draw(x + col * 6 - (col == 0 ? left : 0), y + row * 9 - (row == 0 ? top : 0),
                        x + (col + 1) * 6 + (col == terminal.getWidth() - 1 ? right : 0),
                        y + (row + 1) * 9 + (row == terminal.getHeight() - 1 ? bottom : 0),
                        color(terminal, col, row, false), 250 / 256.0F, 250 / 256.0F, 252 / 256.0F, 252 / 256.0F);
            }
        }
    }
    public static void cursor(Terminal terminal, float x, float y, Quad out){
        int col = terminal.getCursorX(), row = terminal.getCursorY();
        if(!terminal.getCursorBlink() || col < 0 || row < 0 || col >= terminal.getWidth() || row >= terminal.getHeight()) return;
        long ink = ComputerCraftTerminalColors.colors(terminal).foreground();
        int color = ink == TerminalColors.PALETTE ? terminal.getPalette().getRenderColours(15 - terminal.getTextColour()) : render(terminal, (int)ink);
        glyph(out, x + col * 6, y + row * 9, '_', color);
    }
    private static void glyph(Quad out, float x, float y, int ch, int color){
        if(ch == 0 || ch == 32) return;
        if(ch > 255) ch = '?';
        int u = 1 + ch % 16 * 8, v = 1 + ch / 16 * 11;
        out.draw(x, y, x + 6, y + 9, color, u / 256.0F, v / 256.0F, (u + 6) / 256.0F, (v + 9) / 256.0F);
    }
    private static int color(Terminal terminal, int x, int y, boolean text){
        long ink = ComputerCraftTerminalColors.colors(terminal).cell(x, y, text);
        if(ink != TerminalColors.PALETTE) return render(terminal, (int)ink);
        char idx = (text ? terminal.getTextColourLine(y) : terminal.getBackgroundColourLine(y)).charAt(x);
        int slot = Character.digit(idx, 16);
        return terminal.getPalette().getRenderColours(15 - (slot < 0 ? 15 : slot));
    }
    private static int render(Terminal terminal, int color){
        if(terminal.isColour()) return color;
        int grey = ((color >> 16 & 255) + (color >> 8 & 255) + (color & 255)) / 3;
        return color & 0xFF000000 | grey << 16 | grey << 8 | grey;
    }
}
