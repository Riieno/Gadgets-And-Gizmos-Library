package com.rieno.gadgetsandgizmos.lib.client.tablet;

// Draw and handle the client portion of one registered tablet app
public interface TabletAppClientRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/
    TabletAppClientState createScreenState();

    // Check if the app draws all content below the host status bar
    default boolean ownsAppSurface(){
        return false;
    }

    // Draw the tablet app client
    void render(TabletAppClientContext ctx, TabletAppClientState state);

    // Handle mouse clicked
    default boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState state,
                                 double mouseX, double mouseY, int btn) {
        return false;
    }

    default boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState state,
                                  double mouseX, double mouseY, double scrollX, double scrollY){
        return false;
    }

    default boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState state,
                               int keyCode, int scanCode, int modifiers){
        return false;
    }

    default boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState state,
                              char codePoint, int modifiers){
        return false;
    }
    
}
