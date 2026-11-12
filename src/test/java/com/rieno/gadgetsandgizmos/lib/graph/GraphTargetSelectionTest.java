package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verify saved targets stay independent from explicit graph overrides
class GraphTargetSelectionTest{
    // Copy the saved target when the bound input has no value
    @Test
    void missingInputCopiesSavedSelection(){
        CompoundTag saved = target("saved");
        CompoundTag res = GraphTargetSelection.resolveNamedBinding(saved, null, null, name -> null);
        assertEquals(saved, res);
        res.putString("NodeId", "changed");
        assertEquals("saved", saved.getString("NodeId"));
        assertTrue(GraphTargetSelection.resolveNamedBinding(null, null, null, name -> null).isEmpty());
    }

    // Replace or clear a saved target using an explicit Target input
    @Test
    void explicitTargetsOverrideSavedSelection(){
        CompoundTag saved = target("saved");
        CompoundTag live = target("live");
        CompoundTag res = GraphTargetSelection.resolveNamedBinding(saved, "target", live, name -> null);
        assertEquals(live, res);
        res.remove("NodeId");
        assertEquals("live", live.getString("NodeId"));
        assertTrue(GraphTargetSelection.resolveNamedBinding(saved, "target", new CompoundTag(), name -> null).isEmpty());
    }

    // Keep named and unresolved bindings authoritative over the saved target
    @Test
    void namesOverrideWithoutFallingBackWhenUnavailable(){
        CompoundTag saved = target("saved");
        CompoundTag named = new CompoundTag();
        named.putString("Value", "camera");
        assertEquals(target("named"), GraphTargetSelection.resolveNamedBinding(saved, "string", named,
                name -> "camera".equals(name) ? target("named") : null));
        assertTrue(GraphTargetSelection.resolveNamedBinding(saved, "string", named, name -> null).isEmpty());
        assertEquals("saved", saved.getString("NodeId"));
    }

    // Build a detached target payload for selection checks
    private static CompoundTag target(String id){
        CompoundTag res = new CompoundTag();
        res.putString("NodeId", id);
        return res;
    }
}
