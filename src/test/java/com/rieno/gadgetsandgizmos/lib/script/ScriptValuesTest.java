package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDataValue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScriptValuesTest{
    @Test void typedTablesReachOnlyDeclaredOutputs(){
        var res = ScriptValues.outputs(Map.of("items", Map.of(2.0, "b", 1.0, "a"), "value", 42,
                        "extra", "ignored"), Map.of("items", "list", "value", "number", "enabled", "boolean"));
        assertEquals(List.of("a", "b"), res.get("items").value());
        assertEquals(42, res.get("value").asNumber());
        assertFalse(res.get("enabled").asBoolean());
        assertFalse(res.containsKey("extra"));
    }

    @Test void nestedGraphValuesBecomeOrdinaryScriptData(){
        Object val = ScriptValues.encode(GraphValue.map(Map.of("values", GraphValue.list(List.of(GraphValue.number(3))))));
        assertEquals(Map.of("values", List.of(3.0)), val);
    }

    @Test void anyAndNestedTablesInferOneBasedLists(){
        var res = ScriptValues.outputs(Map.of("any", Map.of(1.0, "a", 2.0, "b"),
                        "map", Map.of("nested", Map.of(1.0, 3))), Map.of("any", "any", "map", "map"));
        assertEquals("list", res.get("any").type());
        assertEquals(List.of("a", "b"), res.get("any").value());
        assertEquals(Map.of("nested", List.of(3.0)), res.get("map").value());
    }

    @Test void targetReferencesPreserveExactLongsAndUuidArrays(){
        var data = new GraphDataValue.CompoundValue(Map.of(
                "Pos", new GraphDataValue.NumericValue(GraphDataValue.NumberKind.LONG, Long.MAX_VALUE),
                "UUID", new GraphDataValue.IntArrayValue(List.of(Integer.MIN_VALUE, 2, 3, Integer.MAX_VALUE)),
                "Name", new GraphDataValue.StringValue("Target")));
        var target = new GraphValue("target", data);
        Object encoded = ScriptValues.encode(target);
        var res = ScriptValues.outputs(Map.of("target", encoded), Map.of("target", "target"));
        assertEquals(target, res.get("target"));
    }

    @Test void unsignedLuaColorsRetainTheirArgbBits(){
        var res = ScriptValues.outputs(Map.of("color", 0xFF112233L), Map.of("color", "color"));
        assertEquals((double)0xFF112233, res.get("color").asNumber());
    }

    @Test void genericOutputsInferEachLuaValueAndPreserveSemanticReferences(){
        var target = new GraphValue("target", new GraphDataValue.CompoundValue(Map.of("Pos",
                new GraphDataValue.NumericValue(GraphDataValue.NumberKind.LONG, Long.MAX_VALUE))));
        var res = ScriptValues.outputs(Map.of("number", 12, "text", "hello", "flag", true,
                        "array", Map.of(1.0, 3, 2.0, Map.of("flag", false)), "object", Map.of("answer", 42),
                        "target", ScriptValues.encode(target)),
                Map.of("number", "any", "text", "any", "flag", "any", "array", "any", "object", "any", "target", "any"));
        assertEquals("number", res.get("number").type());
        assertEquals("string", res.get("text").type());
        assertEquals("boolean", res.get("flag").type());
        assertEquals("list", res.get("array").type());
        assertEquals("map", res.get("object").type());
        assertEquals(target, res.get("target"));
        assertTrue(LuaScripts.value(GraphValue.map(Map.of("nested", List.of(true, "line\ntext")))).contains("[\"nested\"] = { true, \"line\\010text\" }"));
    }

    @Test void malformedAndUnboundedResultsAreRejected(){
        var cycle = new HashMap<String, Object>();
        cycle.put("cycle", cycle);
        assertThrows(IllegalArgumentException.class, () -> ScriptValues.outputs(cycle, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ScriptValues.outputs(Map.of("x", Double.NaN), Map.of("x", "number")));
        assertThrows(IllegalArgumentException.class, () -> ScriptValues.outputs(Map.of("x", "bad"), Map.of("x", "number")));
        assertThrows(IllegalArgumentException.class, () -> ScriptValues.outputs(Map.of("x", Map.of(2.0, "bad")), Map.of("x", "list")));
        assertThrows(IllegalArgumentException.class, () -> ScriptValues.outputs(Map.of("x", "x".repeat(32769)), Map.of("x", "string")));
        Object nested = "x";
        for(int idx = 0; idx < 20; idx++) nested = Map.of("nested", nested);
        Object tooDeep = nested;
        assertThrows(IllegalArgumentException.class, () -> ScriptValues.outputs(Map.of("x", tooDeep), Map.of("x", "map")));
    }
}
