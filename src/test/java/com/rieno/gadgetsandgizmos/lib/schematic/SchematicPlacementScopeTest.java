package com.rieno.gadgetsandgizmos.lib.schematic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Keep failed nested copies from changing later world loads or item placement
class SchematicPlacementScopeTest{
    // Restore enclosing copy state after an exception and then clear the thread-local marker
    @Test
    void nestedScopesRestoreStateAfterFailure(){
        assertFalse(SchematicPlacementScope.isActive());
        try(var outer = SchematicPlacementScope.enter()){
            assertTrue(SchematicPlacementScope.isActive());
            assertThrows(IllegalArgumentException.class, () -> {
                try(var inner = SchematicPlacementScope.enter()){ throw new IllegalArgumentException("rejected placement"); }
            });
            assertTrue(SchematicPlacementScope.isActive());
        }
        assertFalse(SchematicPlacementScope.isActive());
    }
}
