package com.rieno.gadgetsandgizmos.lib.script;

import java.util.List;
import java.util.Map;

// Retain zero-based arguments for methods which accept a whole argument list
public record ScriptArguments(List<Object> values){
    public ScriptArguments{ values = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values)); }
    public int getInt(int idx){
        Object val = get(idx);
        if(!(val instanceof Number num) || !Double.isFinite(num.doubleValue()) || num.doubleValue() != num.intValue())
            throw new ScriptException("Argument " + (idx + 1) + " must be an integer");
        return num.intValue();
    }
    public Object get(int idx){ if(idx >= values.size()) throw new ScriptException("Missing argument " + (idx + 1)); return values.get(idx); }
    public Map<?, ?> getTableUnsafe(int idx){
        if(!(get(idx) instanceof Map<?, ?> map)) throw new ScriptException("Argument " + (idx + 1) + " must be a table");
        return map;
    }
}
