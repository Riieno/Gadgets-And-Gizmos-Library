package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletTabDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;

// Give a tablet app renderer its safe snapshot, canvas and action bridge
public record TabletAppClientContext(
        TabletAppDefinition app,
        TabletTabDefinition tab,
        CompoundTag data,
        GuiGraphics graphics,
        Font font,
        int left,
        int top,
        int width,
        int height,
        TabletAppClientSurface surface,
        int mouseX,
        int mouseY,
        ActionSender actions,
        Runnable refresh
) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the tablet app client context
    public TabletAppClientContext {
        data = data == null ? new CompoundTag() : data.copy();
        surface = surface == null ? new TabletAppClientSurface(left, top, width, height) : surface;
        actions = actions == null ? (action, val) -> { } : actions;
        refresh = refresh == null ? () -> { } : refresh;
    }

    // Preserve the content-only client context API
    public TabletAppClientContext(TabletAppDefinition app, TabletTabDefinition tab, CompoundTag data,
                                  GuiGraphics graphics, Font font, int left, int top, int width, int height,
                                  int mouseX, int mouseY, ActionSender actions, Runnable refresh) {
        this(app, tab, data, graphics, font, left, top, width, height,
                new TabletAppClientSurface(left, top, width, height), mouseX, mouseY, actions, refresh);
    }

    // Expose the action sender
    @FunctionalInterface
    public interface ActionSender {
        // Send the action sender
        void send(String actionId, String val);
    }
}
