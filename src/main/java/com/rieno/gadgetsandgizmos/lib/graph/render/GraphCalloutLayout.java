package com.rieno.gadgetsandgizmos.lib.graph.render;

// Keep a callout's authored bounds independent of the visible viewport
public record GraphCalloutLayout(double x, double y, int width, int height){
    // Retain finite graph coordinates and positive content dimensions
    public GraphCalloutLayout{
        if(!Double.isFinite(x) || !Double.isFinite(y) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid graph callout bounds");
    }

    // Center the callout above its node with a fixed graph-space gap
    public static GraphCalloutLayout above(double x, double y, double nodeWidth, int width, int height){
        return new GraphCalloutLayout(x + (nodeWidth - width) / 2, y - height - 10, width, height);
    }

    // Map screen input back into the same coordinates used to draw the callout
    public boolean contains(GraphViewport viewport, double screenX, double screenY){
        double localX = viewport.graphX(screenX) - x;
        double localY = viewport.graphY(screenY) - y;
        return localX >= 0 && localX < width && localY >= 0 && localY < height;
    }

    // Cull projected bounds without clamping or moving the authored layout
    public boolean intersects(GraphViewport viewport, double left, double top, double right, double bottom){
        return viewport.screenX(x) < right && viewport.screenX(x + width) > left
                && viewport.screenY(y) < bottom && viewport.screenY(y + height) > top;
    }
}
