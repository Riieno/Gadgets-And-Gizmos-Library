package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import java.util.Map;
import java.util.UUID;

// Retain the output snapshot at the exact point a script pulses a named execution output
public record ScriptExecPulse(UUID id, long revision, String port, Map<String, GraphValue> outputs){
    public ScriptExecPulse{ outputs = Map.copyOf(outputs); }
}
