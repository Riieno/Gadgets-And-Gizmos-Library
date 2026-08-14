package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.network.chat.Component;

// Handle one validated action dispatched by a registered tablet app
@FunctionalInterface
public interface TabletActionHandler {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the tablet action handler
    Result execute(TabletActionContext ctx, TabletAction action);

    // Store the operation result
    record Result(boolean success, Component message) {
        // Create a successful result
        public static Result success(Component message) {
            return new Result(true, message == null ? Component.empty() : message);
        }

        // Create a failed result
        public static Result failure(Component message) {
            return new Result(false, message == null ? Component.empty() : message);
        }
    }
}
