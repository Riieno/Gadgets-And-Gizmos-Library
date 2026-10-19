package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

// Calculate a readable graph node width from its visible labels
public final class GraphNodeLayout {
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        PRELOAD / SETUP
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph node layout helpers
    private GraphNodeLayout() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Size frame sections from the canvas scale independently of the node width
    public static Frame frame(int height, double scale, int titleSourceHeight, int footerSourceHeight){
        int total = Math.max(0, height);
        double factor = Double.isFinite(scale) ? Math.max(0.1D, scale) : 1;
        int title = Math.min(total, Math.max(1, (int)Math.round(titleSourceHeight * factor)));
        int footer = Math.min(total - title, Math.max(0, (int)Math.round(footerSourceHeight * factor)));
        return new Frame(title, total - title - footer, footer);
    }

    public record Frame(int titleHeight, int bodyHeight, int footerHeight){}

    // Stack distinct port IDs in caller order and retain the occupied body height
    public static PortRows portRows(Collection<String> ports, int rowHeight){
        Map<String, Integer> offsets = new LinkedHashMap<>();
        int height = 0;
        int step = Math.max(1, rowHeight);
        if(ports != null){
            for(String port : ports){
                if(port == null || port.isBlank() || offsets.containsKey(port)) continue;
                offsets.put(port, height);
                height += step;
            }
        }
        return new PortRows(offsets, height);
    }

    // Stack named controls and anonymous spacer/header rows in one pass
    public static PortRows rows(Collection<Row> rows){
        Map<String, Integer> offsets = new LinkedHashMap<>();
        int height = 0;
        if(rows != null){
            for(Row row : rows){
                if(row == null || row.height() <= 0) continue;
                if(row.port() != null && !row.port().isBlank()){
                    if(offsets.containsKey(row.port())) continue;
                    offsets.put(row.port(), height);
                }
                height += row.height();
            }
        }
        return new PortRows(offsets, height);
    }

    public record Row(String port, int height){}

    public record PortRows(Map<String, Integer> offsets, int height){
        public PortRows{
            offsets = Collections.unmodifiableMap(new LinkedHashMap<>(offsets == null ? Map.of() : offsets));
            height = Math.max(0, height);
        }
    }

    // Calculate the width needed to show every supplied label
    public static int width(Collection<String> labels, ToIntFunction<String> measure,
                            int minimum, int maximum, int padding) {
        int min = Math.max(1, minimum);
        int max = Math.max(min, maximum);
        if (labels == null || labels.isEmpty() || measure == null) {
            return min;
        }

        int result = min;
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            result = Math.max(result, measure.applyAsInt(label) + Math.max(0, padding));
        }
        return Math.clamp(result, min, max);
    }
}
