package com.rieno.gadgetsandgizmos.lib.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// Keep script and storage identities separate while sharing one ordered tab strip
public final class ScriptEditorTabs{
    public record Tab(String id, String name, boolean file){
        public String key(){ return (file ? "file\n" : "script\n") + id; }
    }
    private final Map<String, Tab> tabs = new LinkedHashMap<>();
    private String active = "";
    public List<Tab> tabs(){ return List.copyOf(tabs.values()); }
    public Tab active(){ return tabs.get(active); }
    public void open(Tab tab){ tabs.put(tab.key(), tab); active = tab.key(); }
    public boolean activate(String key){ if(!tabs.containsKey(key)) return false; active = key; return true; }
    public void restore(List<Tab> saved, String selected){
        tabs.clear(); saved.forEach(tab -> tabs.putIfAbsent(tab.key(),tab));
        active = tabs.containsKey(selected) ? selected : tabs.isEmpty() ? "" : tabs.keySet().iterator().next();
    }
    public void close(String key){
        var keys = new ArrayList<>(tabs.keySet()); int idx = keys.indexOf(key);
        if(idx < 0) return;
        tabs.remove(key);
        if(!active.equals(key)) return;
        var remaining = new ArrayList<>(tabs.keySet()); active = remaining.isEmpty() ? "" : remaining.get(Math.min(idx, remaining.size() - 1));
    }
    public void retain(Predicate<Tab> keep){ for(var tab : tabs()) if(!keep.test(tab)) close(tab.key()); }
    public void replace(String key, Tab next){
        if(!tabs.containsKey(key)) return;
        Map<String, Tab> replaced = new LinkedHashMap<>();
        tabs.forEach((id, tab) -> replaced.put(id.equals(key) ? next.key() : id, id.equals(key) ? next : tab));
        tabs.clear(); tabs.putAll(replaced); if(active.equals(key)) active = next.key();
    }
}
