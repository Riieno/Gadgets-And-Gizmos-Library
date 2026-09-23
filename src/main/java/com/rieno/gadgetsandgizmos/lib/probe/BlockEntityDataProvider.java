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
        boolean changed = false;
        for (Map.Entry<String, GraphValue> entry : values.entrySet()) {
            String field = entry.getKey();
            GraphValue value = entry.getValue();
            if (field != null && value != null && writable.containsKey(field)) {
                changed |= writeGraphValue(field, value);
            }
        }
        return changed;
    }
}
