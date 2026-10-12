package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// Verify action transitions and labels remain independent of physical movement
class ActionInputStateTest {
    @Test
    void heldActionsOnlyPressOnce() {
        ActionInputState first = ActionInputState.sample(0, 5);
        assertTrue(first.wasPressed(1));
        assertTrue(first.wasPressed(4));
        ActionInputState held = ActionInputState.sample(first.held(), 5);
        assertEquals(0, held.pressed());
        assertEquals(0, held.released());
        assertTrue(held.isHeld(4));
    }

    @Test
    void independentActionsReleaseWithoutDroppingOtherHeldActions() {
        ActionInputState state = ActionInputState.sample(5, 6);
        assertTrue(state.wasReleased(1));
        assertTrue(state.wasPressed(2));
        assertTrue(state.isHeld(4));
        assertFalse(state.wasReleased(4));
    }

    @Test
    void deactivationReleasesEveryAction() {
        ActionInputState state = ActionInputState.sample(31, 0);
        assertEquals(31, state.released());
        assertEquals(0, state.held());
        assertEquals(0, state.pressed());
    }

    @Test
    void labelsResolveTheCurrentAssignmentInsteadOfRetainingTheDefault() {
        AtomicReference<Component> assignment = new AtomicReference<>(Component.literal("Right Button"));
        ActionKeyLabels.register("test.action_input_state.live_label", assignment::get);
        assertEquals("Right Button", ActionKeyLabels.label("test.action_input_state.live_label").getString());
        assignment.set(Component.literal("P"));
        assertEquals("P", ActionKeyLabels.label("test.action_input_state.live_label").getString());
        assertThrows(IllegalArgumentException.class,
                () -> ActionKeyLabels.register("test.action_input_state.live_label", assignment::get));
    }
}
