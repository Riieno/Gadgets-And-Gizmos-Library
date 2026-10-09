package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Check GUI edits, initial binding and wired values without resetting either editor's settings
class GraphBlockSettingsTest{
    @Test
    void initialDefaultsImportBlockSettingsAndCustomInputsUpdateTheBlock(){
        var sync = new GraphBlockSettings();
        var defaults = Map.of("gain", GraphValue.number(5), "enabled", GraphValue.bool(false));
        var block = Map.of("gain", GraphValue.number(10), "enabled", GraphValue.bool(false));
        var graph = Map.of("gain", GraphValue.number(5), "enabled", GraphValue.bool(true));
        var res = sync.reconcile(graph, block, defaults, Set.of());
        assertEquals(Map.of("gain", GraphValue.number(10)), res.graphUpdates());
        assertEquals(Map.of("enabled", GraphValue.bool(true)), res.blockUpdates());
        assertTrue(sync.reconcile(Map.of("gain", GraphValue.number(10), "enabled", GraphValue.bool(true)),
                Map.of("gain", GraphValue.number(10), "enabled", GraphValue.bool(true)), defaults, Set.of()).graphUpdates().isEmpty());
    }

    @Test
    void nativeGuiChangesPullIntoGraphAndWiresTakePriorityOverConcurrentGuiEdits(){
        var sync = new GraphBlockSettings();
        var defaults = Map.of("gain", GraphValue.number(5));
        sync.reconcile(defaults, defaults, defaults, Set.of());
        var block = Map.of("gain", GraphValue.number(8));
        assertEquals(block, sync.reconcile(defaults, block, defaults, Set.of()).graphUpdates());
        var graph = Map.of("gain", GraphValue.number(12));
        assertEquals(graph, sync.reconcile(graph, Map.of("gain", GraphValue.number(9)), defaults, Set.of("gain")).blockUpdates());
        assertEquals(graph, sync.reconcile(graph, Map.of("gain", GraphValue.number(15)), defaults, Set.of("gain")).blockUpdates());
    }
}
