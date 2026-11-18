package com.rieno.gadgetsandgizmos.lib.script;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

// Persist editor identities and layout independently of source buffers and running computers
public record ScriptWorkspaceState(List<ScriptEditorTabs.Tab> tabs, String active, Set<String> expanded,
                                   boolean scriptsCollapsed, boolean filesCollapsed, int scriptHeight,
                                   int resultsWidth, int terminalHeight){
    public ScriptWorkspaceState{
        var unique = new LinkedHashMap<String,ScriptEditorTabs.Tab>();
        if(tabs != null) for(var tab : tabs){
            if(tab == null || tab.id() == null || tab.id().isBlank() || tab.file() && !storageId(tab.id(),true)) continue;
            var next = new ScriptEditorTabs.Tab(tab.id(),tab.name() == null ? tab.id() : tab.name(),tab.file());
            unique.putIfAbsent(next.key(),next);
        }
        tabs = List.copyOf(unique.values());
        active = unique.containsKey(active) ? active : tabs.isEmpty() ? "" : tabs.getFirst().key();
        expanded = expanded == null ? Set.of() : expanded.stream().filter(id -> storageId(id,false)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        scriptHeight = Math.clamp(scriptHeight,-1,16384);
        resultsWidth = Math.clamp(resultsWidth,-1,16384);
        terminalHeight = Math.clamp(terminalHeight,-1,16384);
    }

    // Storage providers own the root key while the library validates the relative path
    public static boolean storageId(String id, boolean file){
        if(id == null) return false;
        int idx = id.indexOf('\n');
        if(idx < 1 || id.lastIndexOf('\n') != idx) return false;
        try{ String path = ScriptFiles.path(id.substring(idx + 1)); return !file || !path.isEmpty(); }
        catch(IllegalArgumentException ex){ return false; }
    }
}
