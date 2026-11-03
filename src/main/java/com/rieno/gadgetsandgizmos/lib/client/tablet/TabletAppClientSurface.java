package com.rieno.gadgetsandgizmos.lib.client.tablet;

// Define the app canvas, including the frame when the renderer owns the whole tablet surface
public record TabletAppClientSurface(int left, int top, int width, int height) {
    public TabletAppClientSurface {
        if(width <= 0 || height <= 0){
            throw new IllegalArgumentException("[G&G-LIB][Tablet] - Tablet app surface dimensions must be positive");
        }
    }
}
