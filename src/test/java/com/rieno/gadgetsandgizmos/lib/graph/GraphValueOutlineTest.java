package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Preserve independent JSON tree branches and literal map keys through live updates
class GraphValueOutlineTest{
    // Expand sibling branches together without replacing their common parent
    @Test void expandsSiblingBranchesInPlace(){
        var root = GraphValue.map(new LinkedHashMap<>(Map.of("first", List.of(1, 2), "second", Map.of("value", false))));
        var tree = new GraphValueOutline();
        assertEquals(3, tree.snapshot(root, 100).rows().size());
        assertTrue(tree.toggle(root, List.of("first")));
        assertTrue(tree.toggle(root, List.of("second")));
        var rows = tree.snapshot(root, 100).rows();
        assertEquals(6, rows.size());
        assertTrue(rows.stream().anyMatch(row -> row.path().equals(List.of("first", "0")) && row.depth() == 2));
        assertTrue(rows.stream().anyMatch(row -> row.path().equals(List.of("second", "value"))));
        tree.toggle(root, List.of("first"));
        assertEquals(4, tree.snapshot(root, 100).rows().size());
        assertTrue(tree.snapshot(root, 100).rows().stream().anyMatch(row -> row.path().equals(List.of("second", "value"))));
    }

    // Treat punctuation and empty keys literally while resolving against fresh data
    @Test void retainsLiteralPathsAndReadsLiveValues(){
        var tree = new GraphValueOutline();
        var first = GraphValue.map(Map.of("a.b/[]", Map.of("", "old")));
        tree.toggle(first, List.of("a.b/[]")); tree.select(first, List.of("a.b/[]", ""));
        assertEquals("old", tree.selectedValue(first).asString());
        var changed = GraphValue.map(Map.of("a.b/[]", Map.of("", "new")));
        assertEquals("new", tree.selectedValue(changed).asString());
        assertTrue(tree.snapshot(changed, 100).rows().stream().anyMatch(row -> row.path().equals(List.of("a.b/[]", ""))));
        var removed = GraphValue.map(Map.of("other", 1));
        assertSame(removed, tree.selectedValue(removed));
        tree.selectedValue(GraphValue.map(Map.of())); assertTrue(tree.selected().isEmpty());
    }

    // Keep large collections bounded and embedded media payloads opaque
    @Test void capsExpandedRowsAndHidesImageMetadata(){
        Map<String, Object> entries = new LinkedHashMap<>();
        for(int idx = 0; idx < 3000; idx++) entries.put(Integer.toString(idx), idx);
        var tree = new GraphValueOutline(); var root = GraphValue.map(entries);
        assertEquals(2048, tree.snapshot(root, 2048).rows().size());
        assertTrue(tree.snapshot(root, 2048).truncated());
        var image = GraphImageValue.of("image:example", Map.of("bytes", "private"));
        assertEquals(1, tree.snapshot(image, 100).rows().size());
        assertFalse(tree.toggle(image, List.of()));
        tree.toggle(root, List.of()); assertEquals(1, tree.snapshot(root, 2048).rows().size());
    }
}
