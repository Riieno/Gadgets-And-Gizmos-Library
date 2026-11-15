package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Publish only changed output values and explicit removals while a script continues running
public record ScriptOutputChange(UUID id, long revision, Map<String, GraphValue> changed, Set<String> removed){
    public ScriptOutputChange{ changed = Map.copyOf(changed); removed = Set.copyOf(removed); }
}
