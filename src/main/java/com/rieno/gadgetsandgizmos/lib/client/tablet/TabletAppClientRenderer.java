package com.rieno.gadgetsandgizmos.lib.client.tablet;

// Draw and handle the client portion of one registered tablet app
public interface TabletAppClientRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the tablet app client
    void render(TabletAppClientContext ctx);

    // Handle mouse clicked
    default boolean mouseClicked(TabletAppClientContext ctx, double mouseX,
                                 double mouseY, int btn) {
        return false;
    }
}
