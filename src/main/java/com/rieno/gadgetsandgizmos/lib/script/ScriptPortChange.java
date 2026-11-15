package com.rieno.gadgetsandgizmos.lib.script;

import java.util.UUID;

// Apply an explicit programmatic interface mutation independently of source and graph saves
public record ScriptPortChange(UUID id, long revision, String name, boolean output, boolean execution, boolean remove, String declaration){
    public ScriptPortChange(UUID id, long revision, String name, boolean output, boolean execution, boolean remove){ this(id, revision, name, output, execution, remove, ""); }
}
