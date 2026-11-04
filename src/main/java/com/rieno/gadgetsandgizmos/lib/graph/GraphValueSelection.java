package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.Set;
import java.util.function.Predicate;

// Select existing node:port keys without scanning graph topology for each value
public final class GraphValueSelection implements Predicate<String>{
    // Retain selected node ids independently of the graph document
    private final Set<String> nodeIds;

    // Capture nodes whose presentation ports need live values
    public GraphValueSelection(Set<String> nodeIds){ this.nodeIds = Set.copyOf(nodeIds); }

    // Match generated node ids and compound port names using selected node prefixes
    @Override
    public boolean test(String key){
        for(int split = key.indexOf(':'); split >= 0; split = key.indexOf(':', split + 1)){
            if(nodeIds.contains(key.substring(0, split))) return true;
        }
        return false;
    }
}
