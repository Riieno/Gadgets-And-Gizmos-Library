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

    default boolean mouseScrolled(TabletAppClientContext ctx, double mouseX, double mouseY, double scrollX, double scrollY){ 
        return false;
    }

    default boolean keyPressed(TabletAppClientContext ctx, int keyCode, int scanCode, int modifiers){
        return false;
    }

    default boolean chatTyped(TabletAppClientContext ctx, char codePoint, int modifiers){
        return false;
    }
}
