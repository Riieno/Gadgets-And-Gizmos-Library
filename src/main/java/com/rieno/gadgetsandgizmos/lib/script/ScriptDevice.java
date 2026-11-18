package com.rieno.gadgetsandgizmos.lib.script;

import java.util.List;
import java.util.Set;

// Describe a host-owned device without loading optional computer APIs
public record ScriptDevice(String name, String type, Set<String> types, Set<String> aliases,
                           Set<String> references, Set<String> methods){
    public ScriptDevice{
        java.util.Objects.requireNonNull(name);
        java.util.Objects.requireNonNull(type);
        types = Set.copyOf(types); aliases = Set.copyOf(aliases);
        references = Set.copyOf(references); methods = Set.copyOf(methods);
    }

    public ScriptPeripheral catalog(){
        return new ScriptPeripheral(name, type, aliases.stream().sorted().toList(), methods.stream().sorted().toList(), List.copyOf(references));
    }
}
