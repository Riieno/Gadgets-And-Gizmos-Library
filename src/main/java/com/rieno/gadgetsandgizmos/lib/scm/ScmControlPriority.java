package com.rieno.gadgetsandgizmos.lib.scm;

import java.util.Set;

/** Keeps direct rotation responsive while altitude hold requests lift. */
public final class ScmControlPriority {
    private static final Set<String> DIRECT_ROTATION = Set.of(
            "ship_yaw", "ship_pan", "ship_yaw_left", "ship_yaw_right",
            "ship_pitch", "ship_tilt", "ship_pitch_up", "ship_pitch_down",
            "ship_roll", "ship_roll_left", "ship_roll_right");

    private ScmControlPriority() {
    }

    public static boolean isDirectRotation(String action) {
        return DIRECT_ROTATION.contains(action);
    }
}
