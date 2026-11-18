package com.rieno.gadgetsandgizmos.lib.script;

import java.util.List;
import java.util.Optional;
import java.util.Set;

// Expose one device's shared control implementation to any script backend
public interface ScriptPeripheralBinding{
    String getType();
    default Set<String> getAdditionalTypes(){ return Set.of(); }
    default Object getTarget(){ return this; }
    default boolean equals(ScriptPeripheralBinding other){ return this == other; }
    // Add explicit bindings when a device exposes methods conditionally
    default void bindMethods(ScriptMethods methods){}

    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "getApiVersion", signature = "getApiVersion(): number", description = "Returns the stable Gadgets & Gizmos CC API version")
    default int getApiVersion(){ return 2; }
    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "methods", signature = "methods(): table", description = "Lists the documented Lua signatures exposed by this peripheral")
    default List<String> methods(){ return ScriptDocumentation.catalog(getClass()).signatures(); }
    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "help", signature = "help([method: string]): table|string", description = "Returns all method help or the help text for one method")
    default Object help(Optional<String> method){
        var docs = ScriptDocumentation.catalog(getClass()).help(); if(method.isEmpty()) return docs;
        String val = docs.get(method.get()); if(val == null) throw new ScriptException("unknown method '" + method.get() + "'"); return val;
    }
}
