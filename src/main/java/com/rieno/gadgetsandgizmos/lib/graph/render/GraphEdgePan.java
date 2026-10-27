package com.rieno.gadgetsandgizmos.lib.graph.render;

// Pan a canvas at a consistent speed while a held drag approaches its edges
public final class GraphEdgePan{
    private static final double MAX_FRAME_SECONDS = 0.1D;
    private final double margin;
    private final double pixelsPerSecond;
    private long lastSampleNanos = Long.MIN_VALUE;

    public GraphEdgePan(double margin, double pixelsPerSecond){
        this.margin = Math.max(0.0D, margin);
        this.pixelsPerSecond = Math.max(0.0D, pixelsPerSecond);
    }

    // Inactive or blocked drags reset the clock so resuming cannot accumulate movement
    public Offset sample(boolean active, double mouseX, double mouseY,
                         double left, double top, double right, double bottom, long nowNanos){
        if(!active || right <= left || bottom <= top){
            lastSampleNanos = Long.MIN_VALUE;
            return Offset.ZERO;
        }
        long previous = lastSampleNanos;
        lastSampleNanos = nowNanos;
        if(previous == Long.MIN_VALUE) return Offset.ZERO;
        double seconds = Math.min(MAX_FRAME_SECONDS, Math.max(0.0D, (nowNanos - previous) / 1_000_000_000.0D));
        return new Offset(velocity(mouseX, left, right) * seconds, velocity(mouseY, top, bottom) * seconds);
    }

    private double velocity(double coordinate, double minimum, double maximum){
        double band = Math.min(margin, (maximum - minimum) * 0.5D);
        if(band <= 0.0D) return 0.0D;
        if(coordinate < minimum + band)
            return pixelsPerSecond * Math.clamp((minimum + band - coordinate) / band, 0.0D, 1.0D);
        if(coordinate > maximum - band)
            return -pixelsPerSecond * Math.clamp((coordinate - maximum + band) / band, 0.0D, 1.0D);
        return 0.0D;
    }

    // Distances are screen pixels; viewport zoom converts any dragged-content compensation
    public record Offset(double x, double y){
        public static final Offset ZERO = new Offset(0.0D, 0.0D);
    }
}
