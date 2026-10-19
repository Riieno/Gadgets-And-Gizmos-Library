package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Reconcile editable graph settings with changes made through a block's own GUI
public final class GraphBlockSettings{
    private Map<String, GraphValue> prevGraph;
    private Map<String, GraphValue> prevBlock;

    // Preserve untouched block settings on binding and give changed or wired graph inputs priority
    public Changes reconcile(Map<String, GraphValue> graph, Map<String, GraphValue> block,
            Map<String, GraphValue> defaults, Set<String> wired){
        Map<String, GraphValue> graphUpdates = new LinkedHashMap<>();
        Map<String, GraphValue> blockUpdates = new LinkedHashMap<>();
        Map<String, GraphValue> nextGraph = new LinkedHashMap<>(graph);
        Map<String, GraphValue> nextBlock = new LinkedHashMap<>(block);
        block.forEach((key, val) -> {
            GraphValue requested = graph.get(key);
            if(requested == null) return;
            boolean changed = prevGraph == null ? !Objects.equals(requested, defaults.get(key))
                    : !Objects.equals(requested, prevGraph.get(key));
            if(wired.contains(key) || changed){
                if(!Objects.equals(requested, val)) blockUpdates.put(key, requested);
                nextBlock.put(key, requested);
            }else if(!Objects.equals(requested, val)){
                if(prevBlock == null || !Objects.equals(val, prevBlock.get(key))){
                    graphUpdates.put(key, val);
                    nextGraph.put(key, val);
                }
            }
        });
        prevGraph = Map.copyOf(nextGraph);
        prevBlock = Map.copyOf(nextBlock);
        return new Changes(graphUpdates, blockUpdates);
    }

    public record Changes(Map<String, GraphValue> graphUpdates, Map<String, GraphValue> blockUpdates){
        public Changes{
            graphUpdates = Map.copyOf(graphUpdates);
            blockUpdates = Map.copyOf(blockUpdates);
        }
    }
}
