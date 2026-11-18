package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Retain file drafts and explicit save revisions independently of the selected editor tab
public final class ScriptFileBuffers{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final Map<String, Buffer> buffers = new LinkedHashMap<>();
    private boolean autosave;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public boolean autosave(){ return autosave; }
    public void autosave(boolean val){ autosave = val; }
    public boolean contains(String id){ return buffers.containsKey(id); }
    public List<String> ids(){ return List.copyOf(buffers.keySet()); }
    public String source(String id){ var buf = buffers.get(id); return buf == null ? null : buf.source; }
    public boolean readOnly(String id){ var buf = buffers.get(id); return buf == null || buf.readOnly; }
    public boolean dirty(String id){ var buf = buffers.get(id); return buf != null && (!buf.source.equals(buf.base) || buf.pending != null || buf.queued != null); }
    public boolean saving(String id){ var buf = buffers.get(id); return buf != null && (buf.pending != null || buf.queued != null); }
    public String error(String id){ var buf = buffers.get(id); return buf == null ? "" : buf.error; }
    public String status(String id){
        var buf = buffers.get(id);
        return buf == null ? "Loading file" : !buf.error.isEmpty() ? "File save failed" : saving(id) ? "Saving file..." : dirty(id) ? "Unsaved changes" : "File saved";
    }

    public void load(String id, String source, boolean readOnly){
        var buf = buffers.computeIfAbsent(id, ignored -> new Buffer(source));
        if(!dirty(id)){ buf.source = source; buf.base = source; }
        buf.readOnly = readOnly;
    }
    public void edit(String id, String source){
        var buf = buffers.get(id); if(buf == null || buf.readOnly || buf.source.equals(source)) return;
        if(source.length() > LuaScripts.MAX_SOURCE_LENGTH) throw new IllegalArgumentException("Script exceeds 32768 characters");
        buf.source = source; buf.error = ""; buf.failed = false;
    }
    public void save(String id){ save(id, true); }
    public void save(String id, boolean retry){
        var buf = buffers.get(id); if(buf == null || buf.readOnly || buf.failed && !retry) return;
        buf.queued = buf.source; buf.failed = false; buf.error = "";
    }
    public void saveAll(){ saveAll(true); }
    public void saveAll(boolean retry){ buffers.keySet().forEach(id -> save(id, retry)); }
    public void automatic(){
        if(!autosave) return;
        buffers.forEach((id, buf) -> { if(!buf.failed && buf.pending == null && dirty(id)) save(id); });
    }

    public ScriptSourceEdits.Save submit(long requestId){
        for(var pair : buffers.entrySet()){
            var buf = pair.getValue(); if(buf.pending != null || buf.queued == null || buf.readOnly) continue;
            String source = buf.queued; buf.queued = null;
            if(source.equals(buf.base)) continue;
            buf.pending = new ScriptSourceEdits.Save(requestId, pair.getKey(), buf.base, source); return buf.pending;
        }
        return null;
    }

    // Flush enabled autosaves when a host stops ticking, retaining the order of already submitted writes
    public ScriptSourceEdits.Save flush(long requestId){
        if(!autosave) return null;
        for(var pair : buffers.entrySet()){
            var buf = pair.getValue(); if(buf.readOnly || buf.failed) continue;
            String prev = buf.pending == null ? buf.base : buf.pending.source();
            if(buf.source.equals(prev)) continue;
            buf.queued = null; buf.pending = new ScriptSourceEdits.Save(requestId, pair.getKey(), prev, buf.source); return buf.pending;
        }
        return null;
    }
    public void acknowledge(long requestId, boolean success, String stored, String message){
        for(var buf : buffers.values()){
            if(buf.pending == null || buf.pending.requestId() != requestId) continue;
            if(success) buf.base = buf.pending.source(); else if(stored != null) buf.base = stored;
            if(!success) buf.queued = null;
            buf.pending = null; buf.failed = !success && !buf.source.equals(buf.base);
            buf.error = buf.failed ? Objects.toString(message, "File save failed") : ""; return;
        }
    }
    public void discard(String id){
        var buf = buffers.get(id); if(buf == null) return;
        buf.source = buf.pending == null ? buf.base : buf.pending.source(); buf.queued = null; buf.error = ""; buf.failed = false;
    }
    public void remove(String id){ buffers.remove(id); }
    public void move(String id, String next){ var buf = buffers.remove(id); if(buf != null) buffers.put(next, buf); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final class Buffer{
        private String base, source, queued, error = "";
        private ScriptSourceEdits.Save pending;
        private boolean readOnly, failed;
        private Buffer(String source){ this.base = source; this.source = source; }
    }
}
