package com.rieno.gadgetsandgizmos.lib.util;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

// Verify updates cannot erase unchanged values from an in-flight baseline
class VersionedValueOverlayTest{
    // Replay newer assignments and removals while retaining unrelated baseline values
    @Test
    void lateBaselinePreservesChangesAndMissingUnchangedValues(){
        var overlay = new VersionedValueOverlay<String, Integer>();
        overlay.record("graph", 8, Map.of("changed", 2, "old", 9), Set.of("removed"));
        overlay.record("graph", 12, Map.of("changed", 3), Set.of("old"));
        assertEquals(Map.of("unchanged", 1, "changed", 3), overlay.merge("graph", 7,
                Map.of("unchanged", 1, "changed", 0, "removed", 7)));
        assertEquals(Map.of("changed", 99), overlay.merge("graph", 20, Map.of("changed", 99)));
    }

    // Do not replay values from a replaced graph or a closed host
    @Test
    void sourceReplacementAndClearReleasePreviousChanges(){
        var overlay = new VersionedValueOverlay<String, Integer>();
        overlay.record("first", 9, Map.of("value", 1), Set.of());
        overlay.record("second", 10, Map.of("next", 2), Set.of());
        assertEquals(Map.of("value", 3), overlay.merge("first", 0, Map.of("value", 3)));
        overlay.clear();
        assertEquals(Map.of(), overlay.merge("second", 0, Map.of()));
    }
}
