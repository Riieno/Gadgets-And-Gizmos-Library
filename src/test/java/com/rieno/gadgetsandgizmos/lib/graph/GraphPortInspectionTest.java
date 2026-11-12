package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Protect inspection precision, bounded collections and opaque media payloads
class GraphPortInspectionTest{
    // Keep unsigned ARGB values from saturating at Java's signed integer limit
    @Test void retainsUnsignedColorsAndAlpha(){
        var port = port("color", GraphValue.number(0xAA12EF34L));
        assertEquals(GraphPortInspection.Preview.COLOR, port.preview());
        assertEquals(0xAA12EF34, GraphPortInspection.color(port.value()));
        assertTrue(port.summary().contains("#AA12EF34"));
        assertEquals(0xFF123456, GraphPortInspection.color(GraphValue.string("#123456")));
    }

    // Show nested types and paths while bounding work on large payloads
    @Test void boundsNestedCollectionsAndKeepsTheirOrder(){
        Map<String, GraphValue> entries = new LinkedHashMap<>();
        entries.put("first", GraphValue.list(List.of(GraphValue.bool(false), GraphValue.number(0))));
        for(int idx = 0; idx < 1000; idx++) entries.put("key" + idx, GraphValue.number(idx));
        var port = port("map", GraphValue.map(entries));
        assertTrue(port.rows().size() <= 12);
        assertEquals("first", port.rows().getFirst().path());
        assertEquals("first.[0]", port.rows().get(1).path());
        assertEquals("false", port.rows().get(1).text());
        assertTrue(port.rows().getLast().text().contains("more entries"));
    }

    // Leave image bytes in the media value instead of expanding them into the bubble
    @Test void hidesEmbeddedImageDataFromText(){
        String src = "data:image/png;base64," + "a".repeat(10000);
        var port = port("any", GraphImageValue.of(src, Map.of("chunks", List.of("private image bytes"))));
        assertEquals(GraphPortInspection.Preview.IMAGE, port.preview());
        assertTrue(port.summary().length() < 100);
        assertTrue(port.rows().isEmpty());
    }

    // Preserve gradient previews when generic graph ports retain the ordered color map
    @Test void recognizesGradientMapsWithoutTreatingOrdinaryNumbersAsColors(){
        Map<String, GraphValue> colors = new LinkedHashMap<>(), numbers = new LinkedHashMap<>();
        for(int idx = 0; idx < GradientGraphNode.RESOLUTION; idx++){
            colors.put(Integer.toString(idx), GraphValue.number(0xFF000000L | idx));
            numbers.put(Integer.toString(idx), GraphValue.number(idx));
        }
        assertEquals(GraphPortInspection.Preview.GRADIENT, port("any", GraphValue.map(colors)).preview());
        assertEquals(100, port("any", GraphValue.map(colors)).colors().size());
        assertEquals("#FF000000", port("any", GraphValue.map(colors)).rows().getFirst().text());
        assertEquals(GraphPortInspection.Preview.NONE, port("map", GraphValue.map(numbers)).preview());
    }

    // Distinguish an unavailable value from valid false and zero values
    @Test void preservesScalarsAndUnavailableState(){
        assertEquals("false", port("boolean", GraphValue.bool(false)).summary());
        assertEquals("0.0", port("number", GraphValue.number(0)).summary());
        double val = 0.12345678901234568;
        assertEquals(Double.toString(val), port("number", GraphValue.number(val)).summary());
        assertEquals("No value available", port("image", null).summary());
        assertEquals(GraphPortInspection.Preview.NONE, port("image", null).preview());
    }

    // Build a single-port snapshot without any Minecraft client state
    private static GraphPortInspection port(String type, GraphValue val){
        return new GraphPortInspection("value", type, true, "Preview", val, null);
    }

    // Show one semantic type even when runtime storage uses a different primitive
    @Test void resolvesSemanticTypesWithoutStorageTypeSuffixes(){
        var text = port("string", GraphValue.number(42));
        assertEquals("string", text.resolvedType()); assertEquals("\"42.0\"", text.summary());
        var bool = port("boolean", GraphValue.number(1));
        assertEquals("boolean", bool.resolvedType()); assertEquals("true", bool.summary());
        assertEquals("false", port("boolean", GraphValue.number(0)).summary());
        assertEquals("number", port("any", GraphValue.number(1)).resolvedType());
        assertEquals("color", port("color", GraphValue.number(-1)).resolvedType());
    }

    // Skip execution flow and prefer the usual value output when a node has several ports
    @Test void selectsPrimaryDataOutputs(){
        Map<String, String> outputs = new LinkedHashMap<>();
        outputs.put("exec", "exec"); outputs.put("details", "map"); outputs.put("value", "number");
        assertEquals("value", GraphPortInspection.primaryOutput(outputs));
        outputs.remove("value"); assertEquals("details", GraphPortInspection.primaryOutput(outputs));
        assertEquals("", GraphPortInspection.primaryOutput(Map.of("exec", "exec")));
    }
}
