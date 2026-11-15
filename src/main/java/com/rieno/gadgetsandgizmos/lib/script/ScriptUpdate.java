package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// Send source and schema only when changed, with individual input replacements and removals
public record ScriptUpdate(String source, Map<String, GraphValue> inputs, Set<String> removed, Map<String, String> outputs){
    public ScriptUpdate{
        inputs = Map.copyOf(inputs);
        removed = Set.copyOf(removed);
        if(outputs != null) outputs = Map.copyOf(outputs);
    }

    public static ScriptUpdate between(ScriptRequest prev, ScriptRequest next){
        Map<String, GraphValue> inputs = new LinkedHashMap<>();
        next.inputs().forEach((port, val) -> {
            if(prev == null || !val.equals(prev.inputs().get(port))) inputs.put(port, val);
        });
        Set<String> removed = new LinkedHashSet<>(prev == null ? Set.of() : prev.inputs().keySet());
        removed.removeAll(next.inputs().keySet());
        return new ScriptUpdate(prev == null || !prev.source().equals(next.source()) ? next.source() : null,
                inputs, removed, prev == null || !prev.outputs().equals(next.outputs()) ? next.outputs() : null);
    }
}
