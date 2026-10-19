package com.rieno.gadgetsandgizmos.lib.client.view;

import java.util.List;

// Keep the painted controls and pointer regions aligned at any surface size
public record ViewControlLayout(int stickX, int stickY, int radius, List<Bounds> modes,
                                Bounds zoom, Bounds flashlight, Bounds tracking, Bounds targetLock, Bounds takeControl, Bounds frame, Bounds grid, Bounds interact, boolean overlay){
    public ViewControlLayout{ modes = List.copyOf(modes); }
    // Preserve layouts supplied before viewfinder and hit interaction controls were exposed
    public ViewControlLayout(int stickX, int stickY, int radius, List<Bounds> modes,
                             Bounds zoom, Bounds flashlight, Bounds tracking, Bounds targetLock, Bounds takeControl, boolean overlay){
        this(stickX, stickY, radius, modes, zoom, flashlight, tracking, targetLock, takeControl,
                new Bounds(0, 0, 0, 0), new Bounds(0, 0, 0, 0), new Bounds(0, 0, 0, 0), overlay);
    }
    // Preserve layouts supplied before remote takeover was exposed
    public ViewControlLayout(int stickX, int stickY, int radius, List<Bounds> modes,
                             Bounds zoom, Bounds flashlight, Bounds tracking, Bounds targetLock, boolean overlay){
        this(stickX, stickY, radius, modes, zoom, flashlight, tracking, targetLock, new Bounds(0, 0, 0, 0), overlay);
    }
    // Retain layouts that supplied tracking before a separate lock button existed
    public ViewControlLayout(int stickX, int stickY, int radius, List<Bounds> modes,
                             Bounds zoom, Bounds flashlight, Bounds tracking, boolean overlay){
        this(stickX, stickY, radius, modes, zoom, flashlight, tracking, new Bounds(0, 0, 0, 0), overlay);
    }
    // Keep layouts supplied by existing hosts source compatible
    public ViewControlLayout(int stickX, int stickY, int radius, List<Bounds> modes,
                             Bounds zoom, Bounds flashlight, boolean overlay){
        this(stickX, stickY, radius, modes, zoom, flashlight, new Bounds(0, 0, 0, 0), overlay);
    }

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
                new Bounds(center + 28, bottom - 34, Math.max(1, right - 61 - center - 28), 22),
                new Bounds(right - 55, bottom - 34, 22, 22),
                new Bounds(right - 29, bottom - 34, 22, 22), false);
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
        int gapY = Math.max(2, (int) Math.round(size * .012));
        int iconTop = y + Math.max(10, (int) Math.round(height * .025));
        int flashSize = Math.max(8, Math.min(Math.max(16, (int) Math.round(size * .05)),
                (stickY - radius - iconTop - 8 - 6 * gapY) / 7));
        int zoomWidth = Math.max(6, (int) Math.round(width * .018));
        int iconLeft = x + Math.max(10, (int) Math.round(width * .018));
        int iconStep = flashSize + gapY;
        return new ViewControlLayout(stickX, stickY, radius, modes,
                new Bounds(x + (int) Math.round(width * .95) - zoomWidth / 2,
                        y + (int) Math.round(height * .78), zoomWidth,
                        Math.max(24, (int) Math.round(height * .20))),
                new Bounds(iconLeft, iconTop, flashSize, flashSize),
                new Bounds(iconLeft, iconTop + iconStep * 1, flashSize, flashSize),
                new Bounds(iconLeft, iconTop + iconStep * 2, flashSize, flashSize),
                new Bounds(iconLeft, iconTop + iconStep * 3, flashSize, flashSize),
                new Bounds(iconLeft, iconTop + iconStep * 4, flashSize, flashSize),
                new Bounds(iconLeft, iconTop + iconStep * 5, flashSize, flashSize),
                new Bounds(iconLeft, iconTop + iconStep * 6, flashSize, flashSize), true);
    }
    // Keep tablet controls below the feed cells
    public static ViewControlLayout chin(int x, int y, int width, int height){
        int radius = Math.max(10, Math.min(18, (height - 12) / 2));
        int controlsX = x + radius * 2 + 16;
        int gap = 3, available = Math.max(7, x + width - controlsX - 6);
        int button = Math.max(8, Math.min(18, (available - 6 * gap) / 7));
        int step = button + gap, top = y + 5;
        int modeWidth = Math.max(12, (available - 2 * gap) / 3);
        var modes = java.util.stream.IntStream.range(0, 3).mapToObj(idx ->
                new Bounds(controlsX + idx * (modeWidth + gap), top + button + 5, modeWidth, 16)).toList();
        return new ViewControlLayout(x + radius + 7, y + height / 2, radius, modes,
                new Bounds(controlsX, y + height - 16, available, 7),
                new Bounds(controlsX, top, button, button), new Bounds(controlsX + step, top, button, button),
                new Bounds(controlsX + step * 2, top, button, button), new Bounds(controlsX + step * 3, top, button, button),
                new Bounds(controlsX + step * 4, top, button, button), new Bounds(controlsX + step * 5, top, button, button),
                new Bounds(controlsX + step * 6, top, button, button), true);
    }
    public record Bounds(int x, int y, int width, int height){
        public boolean contains(double x, double y){
            return x >= this.x && x < this.x + width && y >= this.y && y < this.y + height;
        }
        public int centerX(){ return x + width / 2; }
        public int centerY(){ return y + height / 2; }
    }
}
