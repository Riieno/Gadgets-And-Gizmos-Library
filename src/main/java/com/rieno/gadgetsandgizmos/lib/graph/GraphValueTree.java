package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Browse live map and list values without splitting keys or retaining an obsolete value tree
public final class GraphValueTree{
    // Keep navigation separate from the changing root value supplied by the graph
    private final List<String> path = new ArrayList<>();
    private int offset;

    // Return a detached path whose segments retain punctuation and empty map keys
    public List<String> path(){ return List.copyOf(path); }

    // Report the first child shown on the current page
    public int offset(){ return offset; }

    // Recover to the closest surviving parent when live data removes a selected branch
    public GraphValue current(GraphValue root){
        GraphValue val = root;
        for(int idx = 0; idx < path.size(); idx++){
            GraphValue next = child(val, path.get(idx));
            if(next == null){ path.subList(idx, path.size()).clear(); offset = 0; break; }
            val = next;
        }
        return val;
    }

    // Resolve the current branch's parent so media collections can describe their child semantics
    public GraphValue parent(GraphValue root){
        if(path.isEmpty()) return null;
        GraphValue val = root;
        for(int idx = 0; idx < path.size() - 1; idx++){
            val = child(val, path.get(idx));
            if(val == null) return null;
        }
        return val;
    }

    // Read only one page of direct children while retaining their authored order
    public List<Entry> entries(GraphValue root, int pageSize){
        GraphValue val = current(root);
        int size = size(val);
        if(size == 0 || pageSize <= 0){ offset = 0; return List.of(); }
        offset = Math.clamp(offset, 0, Math.max(0, size - Math.max(1, pageSize)));
        List<Entry> entries = new ArrayList<>();
        if(val != null && val.value() instanceof Map<?, ?> map){
            int idx = 0;
            for(var entry : map.entrySet()){
                if(idx++ < offset) continue;
                if(entries.size() >= pageSize) break;
                entries.add(new Entry(String.valueOf(entry.getKey()), GraphValue.of(entry.getValue())));
            }
        }else if(val != null && val.value() instanceof List<?> list){
            for(int idx = offset; idx < Math.min(list.size(), offset + pageSize); idx++)
                entries.add(new Entry(Integer.toString(idx), GraphValue.of(list.get(idx))));
        }
        return List.copyOf(entries);
    }

    // Descend into exactly one selected entry, including scalar and rich media values
    public boolean enter(GraphValue root, String key){
        if(child(current(root), key) == null) return false;
        path.add(key); offset = 0; return true;
    }

    // Return to the parent without changing the graph's data
    public boolean back(){
        if(path.isEmpty()) return false;
        path.removeLast(); offset = 0; return true;
    }

    // Return to the inspected port's root value
    public void reset(){ path.clear(); offset = 0; }

    // Scroll within the current collection rather than zooming the graph behind it
    public void scroll(GraphValue root, int amount, int pageSize){
        offset = Math.clamp(offset + amount, 0, Math.max(0, size(current(root)) - Math.max(1, pageSize)));
    }

    // Count direct children without expanding nested data or image metadata
    public static int size(GraphValue val){
        if(val == null || GraphPortInspection.preview(val.type(), val) == GraphPortInspection.Preview.IMAGE) return 0;
        if(val.value() instanceof Map<?, ?> map) return map.size();
        return val.value() instanceof List<?> list ? list.size() : 0;
    }

    // Resolve map keys literally and list indices numerically
    private static GraphValue child(GraphValue val, String key){
        if(val == null || GraphPortInspection.preview(val.type(), val) == GraphPortInspection.Preview.IMAGE) return null;
        if(val.value() instanceof Map<?, ?> map){
            for(var entry : map.entrySet()) if(String.valueOf(entry.getKey()).equals(key)) return GraphValue.of(entry.getValue());
        }else if(val.value() instanceof List<?> list){
            try{ int idx = Integer.parseInt(key); if(idx >= 0 && idx < list.size()) return GraphValue.of(list.get(idx)); }
            catch(NumberFormatException ignored){}
        }
        return null;
    }

    // Keep each child key separate from its value and navigation path
    public record Entry(String key, GraphValue value){}
}
