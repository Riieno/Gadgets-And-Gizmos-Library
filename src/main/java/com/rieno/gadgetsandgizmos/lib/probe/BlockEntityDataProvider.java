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

    // Read one typed data value
    GraphValue readGraphValue(String field);

    // Write one typed data value
    boolean writeGraphValue(String field, GraphValue val);
}
