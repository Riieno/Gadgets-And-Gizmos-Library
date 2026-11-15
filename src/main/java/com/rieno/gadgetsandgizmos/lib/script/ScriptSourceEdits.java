package com.rieno.gadgetsandgizmos.lib.script;

import java.util.LinkedHashMap;
import java.util.Map;

// Coalesce code edits independently of graph, schema and runtime changes
public final class ScriptSourceEdits{
    public record Save(long requestId, String id, String previous, String source){}
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public void edit(String id, String previous, String source){
        if(previous.equals(source)) return;
        if(source.length() > LuaScripts.MAX_SOURCE_LENGTH) throw new IllegalArgumentException("Script exceeds 32768 characters");
        Entry entry = entries.computeIfAbsent(id, ignored -> new Entry(previous));
        if(entry.request == null && entry.source.equals(entry.base)) entry.base = previous;
        entry.source = source;
        entry.failed = false;
        entry.error = "";
    }

    public Save submit(long requestId){
        for(var pair : entries.entrySet()){
            Entry entry = pair.getValue();
            if(entry.request != null || entry.failed || entry.source.equals(entry.base)) continue;
            entry.request = new Save(requestId, pair.getKey(), entry.base, entry.source);
            return entry.request;
        }
        return null;
    }

    public Save acknowledge(long requestId, boolean success){ return acknowledge(requestId, success, null, ""); }

    // Rebase a rejected editor on the stored source while preserving its current local text
    public Save acknowledge(long requestId, boolean success, String storedSource, String error){
        for(Entry entry : entries.values()){
            if(entry.request == null || entry.request.requestId() != requestId) continue;
            Save submitted = entry.request;
            entry.request = null;
            if(success) entry.base = submitted.source();
            else if(storedSource != null) entry.base = storedSource;
            entry.failed = !success && !entry.source.equals(entry.base) && entry.source.equals(submitted.source());
            entry.error = entry.failed ? error == null || error.isBlank() ? "Could not save code. Save again to retry." : error : "";
            return submitted;
        }
        return null;
    }

    // Retry failed writes only after an explicit save action
    public void retry(){ entries.values().forEach(entry -> { entry.failed = false; entry.error = ""; }); }
    public void remove(String id){ entries.remove(id); }

    // Protect unsaved source when a host receives unrelated document or port updates
    public Map<String, String> pendingSources(){
        Map<String, String> sources = new LinkedHashMap<>();
        entries.forEach((id, entry) -> { if(entry.request != null || !entry.source.equals(entry.base)) sources.put(id, entry.source); });
        return Map.copyOf(sources);
    }

    public boolean pending(){ return entries.values().stream().anyMatch(entry -> entry.request == null && !entry.failed && !entry.base.equals(entry.source)); }
    public boolean saving(){ return entries.values().stream().anyMatch(entry -> entry.request != null); }
    public String status(String id){ return status(entries.containsKey(id) ? java.util.List.of(entries.get(id)) : java.util.List.of()); }
    public String error(String id){ Entry entry = entries.get(id); return entry == null ? "" : entry.error; }
    public String status(){ return status(entries.values()); }
    private String status(java.util.Collection<Entry> values){
        if(values.stream().anyMatch(entry -> entry.failed)) return "Code save failed";
        if(values.stream().anyMatch(entry -> entry.request == null && !entry.base.equals(entry.source))) return "Unsaved code";
        if(values.stream().anyMatch(entry -> entry.request != null)) return "Saving code...";
        return values.isEmpty() ? "" : "Code saved";
    }

    private static final class Entry{
        private String base;
        private String source;
        private Save request;
        private boolean failed;
        private String error = "";
        private Entry(String base){ this.base = base; this.source = base; }
    }
}
