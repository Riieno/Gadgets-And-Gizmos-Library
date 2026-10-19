package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphPortSectionOrderTest{
    @Test void interleavedTargetsUseContiguousDeclaredRows(){
        assertEquals(List.of("exec", "target", "a_z", "a_x", "a_y", "b_z", "b_x", "status"),
                GraphPortOrder.grouped(List.of("exec", "target", "a_y", "b_x", "a_x", "b_z", "a_z", "status"),
                        List.of(List.of("b_z", "b_x"), List.of("a_z", "a_x", "a_y"))));
    }

    @Test void staleAndOverlappingMembershipNeverDuplicatesOrInventsPorts(){
        assertEquals(List.of("free", "a", "shared", "b"),
                GraphPortOrder.grouped(Arrays.asList("free", "shared", null, "", "b", "a", "a"),
                        List.of(List.of("missing", "a", "shared"), List.of("shared", "b"))));
    }

    @Test void nestedMapChildrenStayWithTheirParentAndTarget(){
        var order = List.of("a_last", "b_first", "grandchild", "child", "a_map", "b_last", "a_first");
        var a = GraphPortOrder.withDescendants(order, List.of("a_first", "a_map", "a_last"),
                Map.of("child", "a_map", "grandchild", "child"));
        assertEquals(List.of("a_first", "a_map", "child", "grandchild", "a_last"), a);
        assertEquals(List.of("a_first", "a_map", "child", "grandchild", "a_last", "b_first", "b_last"),
                GraphPortOrder.grouped(order, List.of(a, List.of("b_first", "b_last"))));
    }

    @Test void cyclicMappingsAndMissingChildrenAreBounded(){
        assertEquals(List.of("root", "child"), GraphPortOrder.withDescendants(
                List.of("root", "child", "unrelated"), List.of("root"),
                Map.of("child", "root", "root", "child", "stale", "root")));
        assertEquals(List.of("root"), GraphPortOrder.withDescendants(List.of("child"), List.of("root"), Map.of()));
    }

    @Test void emptyInputsAndResultsRemainSafe(){
        assertEquals(List.of(), GraphPortOrder.grouped(null, null));
        assertEquals(List.of(), GraphPortOrder.withDescendants(null, null, null));
        assertEquals(List.of("a", "b"), GraphPortOrder.grouped(List.of("a", "b"), List.of(List.of("stale"))));
        assertThrows(UnsupportedOperationException.class,
                () -> GraphPortOrder.withDescendants(List.of(), List.of("root"), Map.of()).add("child"));
    }
}
