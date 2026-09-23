package com.rieno.gadgetsandgizmos.lib.scm;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

// Define the standard gait layouts available to SCM legged control
public enum ScmLeggedGait {
    BIPED("biped", "Bi-pedal", 2),
    TRIPED("triped", "Tri-pedal", 3),
    QUADRUPED("quadruped", "Quadra-pedal", 4),
    PENTAPED("pentaped", "Penta-pedal", 5),
    HEXAPOD("hexapod", "Hexa-pedal", 6),
    HEPTAPOD("heptapod", "Hepta-pedal", 7),
    OCTOPOD("octopod", "Octa-pedal", 8),
    CUSTOM("custom", "Custom", 0);

    private static final List<ScmLeggedGait> SELECTIONS = List.of(values());

    private final String id;
    private final String displayName;
    private final int legCount;

    // Initialize the legged gait
    ScmLeggedGait(String id, String displayName, int legCount) {
        this.id = id;
        this.displayName = displayName;
        this.legCount = legCount;
    }

    // Get the stable serialized id
    public String id() {
        return id;
    }

    // Get the display name
    public String displayName() {
        return displayName;
    }

    // Get the preset leg count, or zero when the profile supplies it
    public int legCount() {
        return legCount;
    }

    // Get every selectable gait in UI order
    public static List<ScmLeggedGait> selections() {
        return SELECTIONS;
    }

    // Resolve a stored gait id
    public static ScmLeggedGait fromId(@Nullable String id) {
        String normalized = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        return SELECTIONS.stream().filter(gait -> gait.id.equals(normalized))
                .findFirst().orElse(QUADRUPED);
    }

    // Check whether a value is a stored gait id
    public static boolean isSelection(@Nullable String id) {
        if (id == null) return false;
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        return SELECTIONS.stream().anyMatch(gait -> gait.id.equals(normalized));
    }

    // Resolve the gait's phase offset for one ordered leg
    public double phaseOffset(int index, int customLegCount) {
        int count = this == CUSTOM ? Math.max(1, customLegCount) : legCount;
        int leg = Math.floorMod(index, count);
        return switch (this) {
            case BIPED -> leg == 0 ? 0.0D : 0.5D;
            case QUADRUPED -> switch (leg) {
                case 0, 3 -> 0.0D;
                default -> 0.5D;
            };
            case HEXAPOD -> leg % 2 == 0 ? 0.0D : 0.5D;
            default -> leg / (double) count;
        };
    }
}
