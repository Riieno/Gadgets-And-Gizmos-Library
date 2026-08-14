package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;

// Define one ordered tablet tab and the actions it exposes
public record TabletTabDefinition(String id, Component title, List<String> actions,
                                  Set<String> keyboardActions) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the tablet tab definition
    public TabletTabDefinition(String id, Component title, List<String> actions) {
        this(id, title, actions, Set.of());
    }

    // Initialize the tablet tab definition
    public TabletTabDefinition {
        id = id == null ? "" : id.trim();
        title = title == null ? Component.literal(id) : title.copy();
        actions = actions == null ? List.of() : List.copyOf(actions);
        keyboardActions = keyboardActions == null ? Set.of() : Set.copyOf(keyboardActions);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the title
    @Override
    public Component title() {
        return title.copy();
    }

    // Check if this requires keyboard
    public boolean requiresKeyboard(String actionId) {
        return actionId != null && keyboardActions.contains(actionId);
    }
}
