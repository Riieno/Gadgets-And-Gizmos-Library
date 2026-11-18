package com.rieno.gadgetsandgizmos.lib.script;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Share immutable method help with every Lua backend
public final class ScriptDocumentation{
    private ScriptDocumentation(){}
    public static Catalog catalog(Class<?> type){
        Map<String, Entry> entries = new TreeMap<>();
        for(var method : type.getMethods()){
            var doc = method.getAnnotation(ScriptMethodDoc.class);
            if(doc == null || doc.hidden()) continue;
            entries.put(doc.name(), new Entry(doc.name(), doc.signature(), doc.description(), List.of(doc.examples()), doc.since(), doc.deprecatedBy()));
        }
        return new Catalog(List.copyOf(entries.values()));
    }
    public record Entry(String name, String signature, String description, List<String> examples, String since, String deprecatedBy){}
    public record Catalog(List<Entry> entries){
        public List<String> signatures(){ return entries.stream().map(Entry::signature).toList(); }
        public Map<String, String> help(){
            Map<String, String> res = new LinkedHashMap<>(); entries.forEach(entry -> res.put(entry.name(), entry.signature() + " - " + entry.description())); return Map.copyOf(res);
        }
    }
}
