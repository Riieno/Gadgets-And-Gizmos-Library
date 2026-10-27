package com.rieno.gadgetsandgizmos.lib.client.ui;

// Fit painted controls and their pointer coordinates to the same centered canvas.
public record GuiCanvasTransform(int x, int y, float scale){
    public GuiCanvasTransform{
        if(!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Canvas scale must be positive");
    }

    public static GuiCanvasTransform fit(int canvasWidth, int canvasHeight, int viewportWidth, int viewportHeight, int padding){
        return fit(canvasWidth, canvasHeight, viewportWidth, viewportHeight, padding, 1);
    }

    // Enlarge or reduce the whole canvas with one scale for both axes
    public static GuiCanvasTransform fit(int canvasWidth, int canvasHeight, int viewportWidth, int viewportHeight,
                                         int padding, float preferredScale){
        if(canvasWidth <= 0 || canvasHeight <= 0) throw new IllegalArgumentException("Canvas dimensions must be positive");
        if(!Float.isFinite(preferredScale) || preferredScale <= 0) throw new IllegalArgumentException("Preferred scale must be positive");
        int inset = Math.max(0, padding);
        float scale = Math.min(preferredScale, Math.min(Math.max(1, viewportWidth - inset * 2) / (float)canvasWidth,
                Math.max(1, viewportHeight - inset * 2) / (float)canvasHeight));
        return new GuiCanvasTransform(Math.max(0, Math.round((viewportWidth - canvasWidth * scale) / 2)),
                Math.max(0, Math.round((viewportHeight - canvasHeight * scale) / 2)), scale);
    }

    public double localX(double screenX){ return (screenX - x) / scale; }
    public double localY(double screenY){ return (screenY - y) / scale; }
    public double screenX(double localX){ return x + localX * scale; }
    public double screenY(double localY){ return y + localY * scale; }
}
