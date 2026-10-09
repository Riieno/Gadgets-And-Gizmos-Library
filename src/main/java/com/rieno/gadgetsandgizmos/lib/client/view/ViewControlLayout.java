package com.rieno.gadgetsandgizmos.lib.client.view;

import java.util.List;

// Keep the painted controls and pointer regions aligned at any surface size
public record ViewControlLayout(int stickX, int stickY, int radius, List<Bounds> modes,
                                Bounds zoom, Bounds flashlight, boolean overlay){
    public ViewControlLayout{ modes = List.copyOf(modes); }

    // Fit compact controls into the tablet's reserved bottom strip
    public static ViewControlLayout compact(int x, int y, int width, int height){
        int center = x + width / 2;
        int left = Math.max(x + 4, center - 142);
        int right = Math.min(x + width - 4, center + 142);
        int bottom = y + height - 4;
        int modeWidth = Math.max(1, (right - left - 8) / 3);
        var modes = java.util.stream.IntStream.range(0, 3).mapToObj(idx ->
                new Bounds(left + 4 + idx * modeWidth, bottom - 68, modeWidth - 2, 17)).toList();
        return new ViewControlLayout(center, bottom - 25, 18, modes,
                new Bounds(left + 7, bottom - 25, center - 28 - left - 7, 13),
                new Bounds(center + 28, bottom - 34, right - 7 - center - 28, 22), false);
    }
    // Float the stick, mode buttons and vertical zoom over an unobstructed feed
    public static ViewControlLayout overlay(int x, int y, int width, int height){
        int size = Math.min(width, height);
        int radius = Math.max(9, (int) Math.round(size * .065));
        int stickX = x + Math.max(radius + 6, (int) Math.round(width * .11));
        int stickY = y + height - radius - Math.max(5, (int) Math.round(height * .03));
        int modeWidth = Math.max(44, (int) Math.round(width * .13));
        int gap = Math.max(4, (int) Math.round(width * .04));
        int modeLeft = x + (width - 3 * modeWidth - 2 * gap) / 2;
        int modeHeight = Math.max(13, (int) Math.round(size * .03));
        int modeTop = y + height - modeHeight - Math.max(5, (int) Math.round(height * .016));
        var modes = java.util.stream.IntStream.range(0, 3).mapToObj(idx ->
                new Bounds(modeLeft + idx * (modeWidth + gap), modeTop, modeWidth, modeHeight)).toList();
        int flashSize = Math.max(16, (int) Math.round(size * .05));
        int zoomWidth = Math.max(6, (int) Math.round(width * .018));
        return new ViewControlLayout(stickX, stickY, radius, modes,
                new Bounds(x + (int) Math.round(width * .95) - zoomWidth / 2,
                        y + (int) Math.round(height * .78), zoomWidth,
                        Math.max(24, (int) Math.round(height * .20))),
                new Bounds(x + Math.max(5, (int) Math.round(width * .018)),
                        stickY - radius - flashSize - Math.max(4, (int) Math.round(height * .008)), flashSize, flashSize), true);
    }
    public record Bounds(int x, int y, int width, int height){
        public boolean contains(double x, double y){
            return x >= this.x && x < this.x + width && y >= this.y && y < this.y + height;
        }
        public int centerX(){ return x + width / 2; }
        public int centerY(){ return y + height / 2; }
    }
}
