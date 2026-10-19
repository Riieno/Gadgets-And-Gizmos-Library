package com.rieno.gadgetsandgizmos.lib.graph.render;

// Convert graph coordinates into screen coordinates for any graph editor renderer
public record GraphViewport(double originX, double originY, double panX, double panY, double zoom) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph viewport
    public GraphViewport {
        zoom = Math.max(0.0001D, zoom);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the screen x
    public double screenX(double graphX) {
        return originX + panX + graphX * zoom;
    }

    // Get the screen y
    public double screenY(double graphY) {
        return originY + panY + graphY * zoom;
    }

    // Get the graph x
    public double graphX(double screenX) {
        return (screenX - originX - panX) / zoom;
    }

    // Get the graph y
    public double graphY(double screenY) {
        return (screenY - originY - panY) / zoom;
    }

    // Center a graph point using the viewport's screen-pixel pan coordinates
    public GraphViewport centerOn(double graphX, double graphY, double canvasWidth, double canvasHeight){
        return new GraphViewport(originX, originY, canvasWidth / 2 - graphX * zoom,
                canvasHeight / 2 - graphY * zoom, zoom);
    }

    // Convert a visible screen distance to graph coordinates
    public double graphDistance(double screenPixels) {
        return screenPixels / zoom;
    }
}
