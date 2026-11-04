package com.rieno.gadgetsandgizmos.lib.client.ui;

// Merge adjacent equal scanlines without changing the pixels of rounded controls
public final class RasterRectangles{
    private RasterRectangles(){}

    @FunctionalInterface
    public interface Sink{
        void fill(int left, int top, int right, int bottom);
    }

    public static void circle(int x, int y, int radius, Sink sink){
        if(radius < 0) return;
        int start = -radius;
        int previous = radius == 0 ? 0 : -1;
        for(int row = -radius; row <= radius; row++){
            int half = (int)Math.sqrt(radius * radius - row * row);
            if(previous >= 0 && half != previous){
                sink.fill(x - previous, y + start, x + previous + 1, y + row);
                start = row;
            }
            previous = half;
        }
        sink.fill(x - previous, y + start, x + previous + 1, y + radius + 1);
    }

    public static void rounded(int x, int y, int width, int height, int radius, Sink sink){
        if(width <= 0 || height <= 0) return;
        int start = 0;
        int previous = -1;
        for(int row = 0; row < height; row++){
            int dy = row < radius ? radius - row - 1 : row >= height - radius ? row - height + radius : 0;
            int inset = radius - (int)Math.sqrt(Math.max(0, radius * radius - dy * dy));
            if(previous >= 0 && inset != previous){
                sink.fill(x + previous, y + start, x + width - previous, y + row);
                start = row;
            }
            previous = inset;
        }
        sink.fill(x + previous, y + start, x + width - previous, y + height);
    }
}
