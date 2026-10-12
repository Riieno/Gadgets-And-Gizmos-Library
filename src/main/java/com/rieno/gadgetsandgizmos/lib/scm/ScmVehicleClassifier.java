package com.rieno.gadgetsandgizmos.lib.scm;

/** Conservative, host-independent vehicle suggestions. Explicit configuration always wins. */
public final class ScmVehicleClassifier {
    private ScmVehicleClassifier() {}

    /** Wings take precedence over landing gear; propulsion alone does not imply a plane. */
    public static String suggest(boolean wheels, boolean liftingSurfaces) {
        return liftingSurfaces ? "plane" : wheels ? "car" : "airship";
    }

    public static boolean isSelection(String selection) {
        return "auto".equals(selection) || "airship".equals(selection)
                || "car".equals(selection) || "plane".equals(selection) || "ik".equals(selection)
                || ScmControlModeRegistry.serializedIds().contains(selection);
    }

    // Filter selectable modes while retaining disabled built-in ids in saved profiles
    public static boolean isAvailableSelection(String selection){
        return "auto".equals(selection) || ScmControlModeRegistry.serializedIds().contains(selection);
    }

    // Keep automatic detection within the host's enabled vehicle modes
    public static String suggestAvailable(boolean wheels, boolean liftingSurfaces){
        String selection = suggest(wheels, liftingSurfaces);
        return isAvailableSelection(selection) ? selection : "airship";
    }
}

