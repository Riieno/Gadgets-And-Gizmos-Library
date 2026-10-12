package com.rieno.gadgetsandgizmos.lib.control;

// Track held actions and their transitions independently of movement state
public record ActionInputState(int held, int pressed, int released) {
    public static final ActionInputState EMPTY = new ActionInputState(0, 0, 0);

    // Derive transitions from one validated input sample
    public static ActionInputState sample(int previous, int current) {
        return new ActionInputState(current, current & ~previous, previous & ~current);
    }

    public boolean isHeld(int action) {
        return (held & action) != 0;
    }

    public boolean wasPressed(int action) {
        return (pressed & action) != 0;
    }

    public boolean wasReleased(int action) {
        return (released & action) != 0;
    }
}
