package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphCollectionOperationsTest{
    @Test void pushPreservesCollectionTypeOrderAndOriginalValues(){
        var list = GraphValue.list(List.of(1, 2));
        var result = GraphCollectionOperations.push(list, GraphValue.number(Math.PI), "unused");
        assertEquals("list", result.type());
        assertEquals(3, result.entries().size());assertEquals(Math.PI, result.entries().get(2).asNumber());
        assertEquals(2, list.entries().size());
        var map = GraphValue.map(GraphPortOrder.entries(Map.entry("zeta", 1), Map.entry("alpha", 2)));
        var pushed = GraphCollectionOperations.push(map, GraphValue.number(3), "middle");
        assertEquals(List.of("zeta", "alpha", "middle"), List.copyOf(((Map<?, ?>)pushed.value()).keySet()));
        var replaced = GraphCollectionOperations.push(pushed, GraphValue.number(4), "alpha");
        assertEquals(List.of("zeta", "alpha", "middle"), List.copyOf(((Map<?, ?>)replaced.value()).keySet()));
        assertEquals(4, replaced.member("alpha").asNumber());assertEquals(2, map.member("alpha").asNumber());
    }

    @Test void popRemovesTheRequestedOrdinalAndKeepsMapKeysAndListOrder(){
        var list = GraphValue.list(List.of("first", "second", "third"));
        var popped = GraphCollectionOperations.pop(list, 1);
        assertTrue(popped.removed());assertEquals("second", popped.value().asString());
        assertEquals(List.of("first", "third"), popped.result().entries().stream().map(GraphValue::asString).toList());
        var map = GraphValue.map(GraphPortOrder.entries(Map.entry("zeta", 1), Map.entry("alpha", Math.PI), Map.entry("middle", 3)));
        var removed = GraphCollectionOperations.pop(map, 1);
        assertEquals(Math.PI, removed.value().asNumber());assertTrue(removed.removed());
        assertEquals(List.of("zeta", "middle"), List.copyOf(((Map<?, ?>)removed.result().value()).keySet()));
        assertEquals(3, ((Map<?, ?>)map.value()).size());
    }

    @Test void invalidPopIndicesLeaveTheCollectionIntact(){
        var list = GraphValue.list(List.of(4));
        for(double index : new double[]{-1, 1, 100, Double.NaN, Double.POSITIVE_INFINITY}){
            var popped = GraphCollectionOperations.pop(list, index);
            assertFalse(popped.removed());assertEquals(list, popped.result());
        }
        assertFalse(GraphCollectionOperations.pop(GraphValue.map(Map.of()), 0).removed());
    }

    @Test void containsAndIndexOfCompareValuesAndKeepFullNumericPrecision(){
        var map = GraphValue.map(GraphPortOrder.entries(Map.entry("zeta", "first"), Map.entry("alpha", "target"), Map.entry("last", "target")));
        assertTrue(GraphCollectionOperations.contains(map, GraphValue.string("target")));
        assertEquals(1, GraphCollectionOperations.indexOf(map, GraphValue.string("target")));
        assertFalse(GraphCollectionOperations.contains(map, GraphValue.string("alpha")));
        assertEquals(-1, GraphCollectionOperations.indexOf(map, GraphValue.string("missing")));
        var list = GraphValue.list(List.of(1, Math.nextUp(1.0), -0.0));
        assertEquals(1, GraphCollectionOperations.indexOf(list, GraphValue.number(Math.nextUp(1.0))));
        assertEquals(2, GraphCollectionOperations.indexOf(list, GraphValue.number(0)));
        assertFalse(GraphCollectionOperations.contains(list, GraphValue.string("1")));
    }

    @Test void nestedValuesCompareEquallyAcrossTypedAndRawCollections(){
        var raw = GraphValue.map(Map.of("value", List.of(1, true)));
        var typed = GraphValue.map(Map.of("value", GraphValue.list(List.of(GraphValue.number(1), GraphValue.bool(true)))));
        assertEquals(0, GraphCollectionOperations.indexOf(GraphValue.list(List.of(raw)), typed));
    }

    @Test void nodeSchemasAndExecutorsCanBeUsedByAnotherHost(){
        var nodes = GraphCollectionNodes.nodes("other_host");
        assertEquals(4, nodes.size());
        assertThrows(IllegalArgumentException.class, () -> GraphCollectionNodes.nodes("bad namespace"));
        var values = new LinkedHashMap<String, GraphValue>();
        values.put("collection", GraphValue.list(List.of(1, 2)));values.put("value", GraphValue.number(2));values.put("index", GraphValue.number(1));
        for(var node : nodes){
            assertTrue(node.definition().id().startsWith("other_host:"));
            assertEquals("list", node.defaults().get("collection").type());
            var outputs = node.executor().execute(null, values);
            assertEquals(node.definition().outputs().keySet(), outputs.keySet());
        }
    }
}
