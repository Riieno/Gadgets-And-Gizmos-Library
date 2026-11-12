package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.color.ColorRamp;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Verify gradient order, opacity and transitions back to solid colors
class GraphColorValueTest{
    @Test void numericKeysRetainTheirOrderRegardlessOfMapIteration(){
        var colors = new LinkedHashMap<String, GraphValue>();
        colors.put("2", GraphValue.number(0xFF0000FF));
        colors.put("0", GraphValue.number(0x00FF0000));
        colors.put("1", GraphValue.number(0x8000FF00));
        var ramp = GraphColorValue.gradient(GraphValue.map(colors)).orElseThrow();
        assertEquals(0x00FF0000, ramp.sample(0));
        assertEquals(0x8000FF00, ramp.sample(.5));
        assertEquals(0xFF0000FF, ramp.sample(1));
        assertEquals(0x40808000, ramp.sample(.25));
        assertEquals(ramp.sample(0), ramp.sample(-10));
        assertEquals(ramp.sample(1), ramp.sample(10));
    }

    @Test void gradientsSurviveResolvedTagsAndSolidValuesRemovePreviousPaint(){
        var elm = new CompoundTag();
        var val = GraphValue.list(List.of(GraphValue.number(0xFFFF0000), GraphValue.number(0xFF0000FF)));
        GraphColorValue.bindGradient(elm, "Color", val);
        var copy = elm.copy();
        assertEquals(List.of(0xFFFF0000, 0xFF0000FF), GraphColorValue.gradient(copy, "Color").orElseThrow().colors());
        GraphColorValue.bindGradient(copy, "Color", GraphValue.number(0xFF00FF00));
        assertFalse(copy.contains(GraphColorValue.GRADIENTS));
        assertTrue(GraphColorValue.gradient(elm, "Color").isPresent());
    }

    @Test void malformedMapsCannotBecomePaint(){
        assertTrue(GraphColorValue.gradient(GraphValue.map(java.util.Map.of("1", 1))).isEmpty());
        assertTrue(GraphColorValue.gradient(GraphValue.list(List.of("red", "blue"))).isEmpty());
        assertTrue(GraphColorValue.gradient(GraphValue.list(List.of())).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ColorRamp(List.of()));
    }
}
