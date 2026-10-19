package com.rieno.gadgetsandgizmos.lib.view;

// Fit a camera capture to its configured longest edge while retaining the viewport aspect ratio
public final class ViewFeedResolution{
    private ViewFeedResolution(){}

    public static Size fit(int width, int height, int resolution){
        int w = Math.max(1, width), h = Math.max(1, height);
        int pixels = Math.clamp(resolution, 64, 2048);
        double scale = pixels / (double)Math.max(w, h);
        return new Size(Math.max(16, (int)Math.round(w * scale)), Math.max(16, (int)Math.round(h * scale)));
    }

    public record Size(int width, int height){}
}
