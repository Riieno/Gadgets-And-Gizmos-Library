package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GraphDynamicPortSchemaTest {
    @Test
    void incompletePreviewRetainsOnlyConnectedPreviousOutputs() {
        CompoundTag previous = new CompoundTag();
        previous.putString("temperature", "number");
        previous.putString("pressure", "number");
        CompoundTag preview = new CompoundTag();
        preview.putString("new_value", "string");

        CompoundTag result = GraphDynamicPortSchema.retainConnectedOutputs(
                preview, previous, List.of("temperature"));

        assertEquals("number", result.getString("temperature"));
        assertEquals("string", result.getString("new_value"));
        assertFalse(result.contains("pressure"));
        assertFalse(preview.contains("temperature"));
    }
}
