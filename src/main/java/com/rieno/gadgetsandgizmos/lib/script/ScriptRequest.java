package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Retain code, live inputs and the persistent interface of one isolated computer
public record ScriptRequest(UUID id, long revision, String source, Map<String, GraphValue> inputs,
                            Map<String, String> outputs, boolean autoStart, Set<String> execInputs, Set<String> execOutputs){
    public ScriptRequest(UUID id, long revision, String source, Map<String, GraphValue> inputs, Map<String, String> outputs){
        this(id, revision, source, inputs, outputs, true);
    }
    public ScriptRequest(UUID id, long revision, String source, Map<String, GraphValue> inputs, Map<String, String> outputs, boolean autoStart){
        this(id, revision, source, inputs, outputs, autoStart, Set.copyOf(ScriptExecPorts.read(source == null ? "" : source).inputs()),
                Set.copyOf(ScriptExecPorts.read(source == null ? "" : source).outputs()));
    }
    public ScriptRequest{
        java.util.Objects.requireNonNull(id);
        source = source == null ? "" : source;
        if(source.length() > LuaScripts.MAX_SOURCE_LENGTH) throw new IllegalArgumentException("Script exceeds 32768 characters");
        inputs = Map.copyOf(inputs); outputs = Map.copyOf(outputs);
        execInputs = Set.copyOf(execInputs); execOutputs = Set.copyOf(execOutputs);
    }
    @Deprecated public boolean passive(){ return false; }
}
