package com.rieno.gadgetsandgizmos.lib.display;

/** Caps a display texture while retaining its source aspect ratio. */
public record DisplayRasterSize(int width, int height) {
    public static DisplayRasterSize fit(int width, int height, int maxWidth, int maxHeight) {
        if (width < 1 || height < 1 || maxWidth < 1 || maxHeight < 1) {
            throw new IllegalArgumentException("Display dimensions must be positive");
        }
        double scale = Math.min(1.0D, Math.min((double) maxWidth / width, (double) maxHeight / height));
        return new DisplayRasterSize(Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale)));
    }
}
