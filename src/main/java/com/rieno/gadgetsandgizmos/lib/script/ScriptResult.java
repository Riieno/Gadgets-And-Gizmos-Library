package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.Map;
import java.util.UUID;

// Return only declared outputs from one completed invocation
public record ScriptResult(UUID id, long revision, Map<String, GraphValue> outputs, String error, boolean executed, long sequence, String inputPort){
    public ScriptResult(UUID id, long revision, Map<String, GraphValue> outputs, String error, boolean executed, long sequence){ this(id, revision, outputs, error, executed, sequence, "exec"); }
    public ScriptResult(UUID id, long revision, Map<String, GraphValue> outputs, String error){ this(id, revision, outputs, error, true); }
    public ScriptResult(UUID id, long revision, Map<String, GraphValue> outputs, String error, boolean executed){ this(id, revision, outputs, error, executed, 0); }
    public ScriptResult{
        outputs = Map.copyOf(outputs);
        error = error == null ? "" : error;
        if(error.length() > 2048) error = error.substring(0, 2048);
    }

    public boolean valid(){ return error.isEmpty(); }

    public static ScriptResult validation(ScriptRequest req, String error){
        return new ScriptResult(req.id(), req.revision(), Map.of(), error, false);
    }

    public static ScriptResult failure(ScriptRequest req, String error){
        return new ScriptResult(req.id(), req.revision(), Map.of(),
                error == null || error.isBlank() ? "Script execution failed" : error);
    }
}
