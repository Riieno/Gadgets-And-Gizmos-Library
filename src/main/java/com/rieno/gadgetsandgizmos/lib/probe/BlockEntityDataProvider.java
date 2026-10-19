package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.List;
import java.util.Map;

// Expose typed readable and writable data from any block entity implementation
public interface BlockEntityDataProvider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the readable data ports
    Map<String, String> graphReadableData();

    // Get the writable data ports
    Map<String, String> graphWritableData();

    // Describe inputs which depend on a selected mode or another input value
    default Map<String, com.rieno.gadgetsandgizmos.lib.graph.GraphInputVisibility.Condition> graphWritableInputConditions(){
        return Map.of();
    }

    // Get the writable data options
    default Map<String, List<String>> graphWritableOptions() {
        return Map.of();
    }

    // Get explicitly grouped readable data ports
    default Map<String, Map<String, String>> graphReadableDataPortGroups() {
        return Map.of();
    }

    // Get explicitly grouped writable data ports
    default Map<String, Map<String, String>> graphWritableDataPortGroups() {
        return Map.of();
    }

    // Check whether dynamic graph data ports are ready to be refreshed
    default boolean isGraphDataSchemaReady() {
        return true;
    }

    // Get the revision for dynamic graph data ports
    default long graphDataSchemaRevision() {
        return 0L;
    }

    // Read one typed data value
    GraphValue readGraphValue(String field);

    // Write one typed data value
    boolean writeGraphValue(String field, GraphValue val);

    // Write a batch of typed data values
    default boolean writeGraphValues(Map<String, GraphValue> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        Map<String, String> writable = graphWritableData();
        var conditions = graphWritableInputConditions();
        var selected = com.rieno.gadgetsandgizmos.lib.graph.GraphInputVisibility.filterValues(values, conditions);
        if(selected.isEmpty() && !conditions.isEmpty()) return true;
        java.util.Set<String> selectors = new java.util.LinkedHashSet<>();
        conditions.values().forEach(condition -> selectors.add(condition.selector()));
        Map<String, GraphValue> ordered = new java.util.LinkedHashMap<>();
        selectors.forEach(port -> {
            if(selected.containsKey(port)) ordered.put(port, selected.get(port));
        });
        ordered.putAll(selected);
        boolean changed = false;
        for (Map.Entry<String, GraphValue> entry : ordered.entrySet()) {
            String field = entry.getKey();
            GraphValue value = entry.getValue();
            if (field != null && value != null && writable.containsKey(field)) {
                changed |= writeGraphValue(field, value);
            }
        }
        return changed;
    }
}
