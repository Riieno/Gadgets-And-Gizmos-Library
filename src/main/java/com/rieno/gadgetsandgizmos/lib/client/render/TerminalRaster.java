package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import java.util.Arrays;

// Resolve terminal cells into texture pixels without issuing per-cell rendering commands
final class TerminalRaster{
    private TerminalRaster(){}

    static int[] pixels(ScriptTerminal terminal, int[][] glyphs){
        int width = terminal.width() * 6;
        int[] pixels = new int[width * terminal.height() * 9], palette = new int[16];
        for(int idx = 0; idx < 16; idx++) palette[idx] = abgr(terminal.palette().get(idx));
        for(int row = 0; row < terminal.height(); row++){
            String text = terminal.text().get(row), fg = terminal.foreground().get(row), bg = terminal.background().get(row);
            for(int col = 0; col < terminal.width();){
                int end = col + 1; char color = bg.charAt(col);
                while(end < terminal.width() && bg.charAt(end) == color && terminal.backgroundArgb(row, end) == terminal.backgroundArgb(row, col)) end++;
                for(int y = 0; y < 9; y++){
                    int offset = (row * 9 + y) * width;
                    Arrays.fill(pixels, offset + col * 6, offset + end * 6, abgr(terminal.backgroundArgb(row, col)));
                }
                col = end;
            }
            for(int col = 0; col < terminal.width(); col++){
                int ch = text.charAt(col); if(ch == 0 || ch == 32) continue;
                int[] glyph = glyphs[ch <= 255 ? ch : '?'];
                int front = abgr(terminal.foregroundArgb(row, col)), back = abgr(terminal.backgroundArgb(row, col));
                if(front == back) continue;
                for(int y = 0; y < 9; y++) for(int x = 0; x < 6; x++){
                    int alpha = glyph[y * 6 + x];
                    if(alpha != 0) pixels[(row * 9 + y) * width + col * 6 + x] = alpha == 255 && front >>> 24 == 255 ? front : blend(front, back, alpha);
                }
            }
        }
        return pixels;
    }
    private static int index(char ch){ return ch <= '9' ? ch - '0' : ch - 'a' + 10; }
    private static int abgr(int rgb){ return rgb & 0xFF000000 | (rgb & 255) << 16 | rgb & 0xFF00 | rgb >>> 16 & 255; }
    private static int blend(int front, int back, int alpha){
        alpha = alpha * (front >>> 24) / 255;
        int retained = (back >>> 24) * (255 - alpha) / 255, combined = alpha + retained;
        if(combined == 0) return 0;
        int r = ((front & 255) * alpha + (back & 255) * retained + combined / 2) / combined;
        int g = ((front >>> 8 & 255) * alpha + (back >>> 8 & 255) * retained + combined / 2) / combined;
        int b = ((front >>> 16 & 255) * alpha + (back >>> 16 & 255) * retained + combined / 2) / combined;
        return combined << 24 | b << 16 | g << 8 | r;
    }
}
