package com.rieno.gadgetsandgizmos.lib.scm;

/** Conservative, host-independent vehicle suggestions. Explicit configuration always wins. */
public final class ScmVehicleClassifier {
    private ScmVehicleClassifier() {}

    /** Wings take precedence over landing gear; propulsion alone does not imply a plane. */
    public static String suggest(boolean wheels, boolean liftingSurfaces) {
        return liftingSurfaces ? "plane" : wheels ? "car" : "airship";
    }

    public static boolean isSelection(String selection) {
        return "auto".equals(selection) || ScmControlModeRegistry.serializedIds().contains(selection);
    }
}

