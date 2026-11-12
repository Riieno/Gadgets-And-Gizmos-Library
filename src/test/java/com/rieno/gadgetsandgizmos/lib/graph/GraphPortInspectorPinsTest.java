package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// Keep per-port visibility durable without coupling it to graph saves
class GraphPortInspectorPinsTest{
    @TempDir Path dir;

    // Reopening the preference store represents a new client session
    @Test void survivesReopeningAndUnpinsTheSelectedSideOnly() throws Exception{
        Path file = dir.resolve("nested/pins.json");
        var input = new GraphPortInspectorPins.Port("node", "value", false);
        var output = new GraphPortInspectorPins.Port("node", "value", true);
        var pins = new GraphPortInspectorPins(file);
        pins.setPinned("world/controller/main", input, true);
        pins.setPinned("world/controller/main", output, true);
        var reopened = new GraphPortInspectorPins(file);
        assertEquals(2, reopened.ports("world/controller/main").size());
        reopened.setPinned("world/controller/main", input, false);
        var restarted = new GraphPortInspectorPins(file);
        assertEquals(java.util.Set.of(output), restarted.ports("world/controller/main"));
    }

    // Preserve other controllers, functions and updates from another open screen
    @Test void isolatesScopesAndMergesNewerFileContents() throws Exception{
        Path file = dir.resolve("pins.json");
        var port = new GraphPortInspectorPins.Port("same-node", "items.with.dots", true);
        var first = new GraphPortInspectorPins(file);
        var second = new GraphPortInspectorPins(file);
        first.setPinned("controller-a/main", port, true);
        second.setPinned("controller-b/main", port, true);
        first.setPinned("controller-a/function", port, true);
        var reopened = new GraphPortInspectorPins(file);
        for(String scope : java.util.List.of("controller-a/main", "controller-b/main", "controller-a/function"))
            assertTrue(reopened.ports(scope).contains(port));
        assertTrue(reopened.ports("another-world").isEmpty());
    }

    // Keep corrupt files visible as errors rather than overwriting the player's state
    @Test void rejectsUnreadablePreferences() throws Exception{
        Path file = dir.resolve("pins.json");
        Files.writeString(file, "{not-json");
        assertThrows(java.io.IOException.class, () -> new GraphPortInspectorPins(file));
        assertEquals("{not-json", Files.readString(file));
    }

    // Restore authored graph placement and presentation mode after a new client session
    @Test void persistsGraphPlacementAndModesWithoutChangingOtherPins() throws Exception{
        Path file = dir.resolve("pins.json");
        var port = new GraphPortInspectorPins.Port("node", "map", true);
        var other = new GraphPortInspectorPins.Port("other", "value", false);
        var pins = new GraphPortInspectorPins(file);
        pins.setPinned("graph", port, true); pins.setPinned("graph", other, true);
        var state = new GraphPortInspectorPins.State(-124.5, 975.25, GraphPortInspectorPins.Mode.SIMPLE);
        pins.setState("graph", port, state);
        var reopened = new GraphPortInspectorPins(file);
        assertEquals(state, reopened.state("graph", port)); assertNull(reopened.state("graph", other));
        reopened.setPinned("graph", port, true); assertEquals(state, reopened.state("graph", port));
        reopened.setPinned("graph", port, false); reopened.setState("graph", port, state);
        assertEquals(java.util.Set.of(other), new GraphPortInspectorPins(file).ports("graph"));
    }

    // Read visibility-only files without interpreting a missing position as the graph origin
    @Test void readsLegacyPinsWithoutPlacement() throws Exception{
        Path file = dir.resolve("pins.json");
        Files.writeString(file, "[{\"scope\":\"graph\",\"port\":{\"nodeId\":\"node\",\"name\":\"map\",\"output\":true}}]");
        var pins = new GraphPortInspectorPins(file); var port = pins.ports("graph").iterator().next();
        assertNull(pins.state("graph", port));
        assertTrue(pins.ports("graph").contains(port));
    }
}
