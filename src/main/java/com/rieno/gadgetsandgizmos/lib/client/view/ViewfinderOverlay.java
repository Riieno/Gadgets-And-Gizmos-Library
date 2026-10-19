package com.rieno.gadgetsandgizmos.lib.client.view;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Locale;

// Draw a camcorder frame and an independent composition grid on any GUI or projected canvas
public final class ViewfinderOverlay{
    private ViewfinderOverlay(){}

    public static void render(ViewControlPanel.Canvas canvas, Font font, int x, int y, int width, int height,
                              boolean frame, boolean grid, double fov, long elapsedMillis){
        render(canvas, font, x, y, width, height, frame, grid, fov, elapsedMillis, 0);
    }
    // Reserve room for controls while keeping the aiming reticle at the actual lens centre
    public static void render(ViewControlPanel.Canvas canvas, Font font, int x, int y, int width, int height,
                              boolean frame, boolean grid, double fov, long elapsedMillis, int bottomInset){
        render(canvas, font, x, y, width, height, frame, grid, fov, elapsedMillis, bottomInset, 0, 0);
    }
    // Keep status text clear of host controls while the frame and grid retain the image bounds
    public static void render(ViewControlPanel.Canvas canvas, Font font, int x, int y, int width, int height,
                              boolean frame, boolean grid, double fov, long elapsedMillis, int bottomInset,
                              int statusLeftInset, int statusBottomInset){
        if(width < 16 || height < 16) return;
        int pad = Math.max(3, Math.min(width, height) / 32);
        int left = x + pad, top = y + pad, right = x + width - pad, bottom = y + height - pad - Math.max(0, bottomInset);
        if(bottom - top < 8) return;
        if(grid){
            for(int idx = 1; idx < 3; idx++){
                int gx = left + (right - left) * idx / 3;
                int gy = top + (bottom - top) * idx / 3;
                canvas.fill(gx, top, gx + 1, bottom, 0x68DCE8E3);
                canvas.fill(left, gy, right, gy + 1, 0x68DCE8E3);
            }
        }
        if(!frame) return;
        int length = Math.max(4, Math.min(width, height) / 12);
        int color = 0xDFE7EFDF;
        for(int cx : new int[]{left, right}) for(int cy : new int[]{top, bottom}){
            int x1 = cx == left ? cx : cx - length;
            int y1 = cy == top ? cy : cy - length;
            canvas.fill(x1, cy, x1 + length, cy + 1, color);
            canvas.fill(cx, y1, cx + 1, y1 + length, color);
        }
        int midX = x + width / 2, midY = y + height / 2;
        canvas.fill(midX - 6, midY, midX - 2, midY + 1, color);
        canvas.fill(midX + 3, midY, midX + 7, midY + 1, color);
        canvas.fill(midX, midY - 6, midX + 1, midY - 2, color);
        canvas.fill(midX, midY + 3, midX + 1, midY + 7, color);
        if(width < 128 || height < 72) return;
        int statusLeft = left + Math.max(0, statusLeftInset);
        if(elapsedMillis / 600 % 2 == 0) canvas.fill(statusLeft + 7, top + 9, statusLeft + 11, top + 13, 0xFFE06B64);
        canvas.drawString(font, Component.translatable("gadgetsngizmos.camera.live").getString(), statusLeft + 15, top + 7, color, true);
        String lens = String.format(Locale.ROOT, "FOV %.0f", fov);
        canvas.drawString(font, lens, right - font.width(lens) - 7, top + 7, color, true);
        long seconds = Math.max(0, elapsedMillis / 1000);
        String time = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        canvas.drawString(font, time, statusLeft + 7, bottom - font.lineHeight - 7 - Math.max(0, statusBottomInset), color, true);
    }
}
