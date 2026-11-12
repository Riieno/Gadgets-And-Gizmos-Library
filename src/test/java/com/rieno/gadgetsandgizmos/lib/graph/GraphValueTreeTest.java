package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Protect map keys, list navigation and recovery while inspected values change
class GraphValueTreeTest{
    // Treat punctuation and empty strings as literal map keys at every depth
    @Test void navigatesMapAndListBranchesWithoutSplittingKeys(){
        var root = GraphValue.map(Map.of("a.b/[]", GraphValue.list(List.of(
                GraphValue.map(Map.of("", GraphValue.bool(false)))))));
        var tree = new GraphValueTree();
        assertTrue(tree.enter(root, "a.b/[]"));
        assertEquals("0", tree.entries(root, 8).getFirst().key());
        assertTrue(tree.enter(root, "0"));
        assertEquals("list", tree.parent(root).type());
        assertTrue(tree.enter(root, ""));
        assertEquals("map", tree.parent(root).type());
        assertFalse(tree.current(root).asBoolean());
        assertEquals(List.of("a.b/[]", "0", ""), tree.path());
        assertTrue(tree.back());
        assertEquals("map", tree.current(root).type());
        tree.reset(); assertTrue(tree.path().isEmpty()); assertNull(tree.parent(root));
        assertFalse(tree.enter(root, "missing"));
    }

    // Return to a surviving branch when a live update removes a selected list entry
    @Test void followsLiveDataAndRecoversFromRemovedBranches(){
        var first = GraphValue.map(Map.of("items", GraphValue.list(List.of("old", "selected"))));
        var tree = new GraphValueTree();
        tree.enter(first, "items"); tree.enter(first, "1");
        var updated = GraphValue.map(Map.of("items", GraphValue.list(List.of("new", "changed"))));
        assertEquals("changed", tree.current(updated).asString());
        var removed = GraphValue.map(Map.of("items", GraphValue.list(List.of("new"))));
        assertEquals("list", tree.current(removed).type());
        assertEquals(List.of("items"), tree.path());
        assertEquals("map", tree.current(GraphValue.map(Map.of())).type());
        assertTrue(tree.path().isEmpty());
    }

    // Page through every direct child without flattening nested content or image metadata
    @Test void pagesOrderedEntriesAndKeepsImagesOpaque(){
        Map<String, Object> entries = new LinkedHashMap<>();
        for(int idx = 0; idx < 40; idx++) entries.put("entry" + idx, idx);
        var root = GraphValue.map(entries); var tree = new GraphValueTree();
        assertEquals("entry0", tree.entries(root, 8).getFirst().key());
        tree.scroll(root, 100, 8);
        assertEquals("entry32", tree.entries(root, 8).getFirst().key());
        assertEquals("entry39", tree.entries(root, 8).getLast().key());
        tree.scroll(root, -100, 8); assertEquals(0, tree.offset());
        var image = GraphImageValue.of("image:example", Map.of("bytes", "hidden"));
        assertEquals(0, GraphValueTree.size(image));
        assertTrue(tree.entries(image, 8).isEmpty());
        assertFalse(tree.enter(image, "bytes"));
    }
}
