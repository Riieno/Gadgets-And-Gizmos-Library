package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Expand independent branches of a live value as an ordered JSON-style tree
public final class GraphValueOutline{
    // Keep paths as literal segments so punctuation in keys never changes tree identity
    private final Set<List<String>> expanded = new HashSet<>();
    private List<String> selected = List.of();

    // Open the root while leaving nested collections compact
    public GraphValueOutline(){ expanded.add(List.of()); }

    // Return the selected row without exposing mutable navigation state
    public List<String> selected(){ return selected; }

    // Resolve a selection against the latest value instead of retaining an obsolete payload
    public GraphValue selectedValue(GraphValue root){
        GraphValue val = resolve(root, selected);
        if(val == null){ selected = List.of(); return root; }
        return val;
    }

    // Select scalar or rich rows without replacing the root tree
    public void select(GraphValue root, List<String> path){
        if(resolve(root, path) != null) selected = List.copyOf(path);
    }

    // Open or close one collection while preserving the state of its sibling branches
    public boolean toggle(GraphValue root, List<String> path){
        if(!isCollection(resolve(root, path))) return false;
        List<String> key = List.copyOf(path);
        if(!expanded.remove(key)) expanded.add(key);
        return true;
    }

    // Visit only expanded branches and cap the rendered outline independently of collection size
    public Snapshot snapshot(GraphValue root, int maximumRows){
        List<Row> rows = new ArrayList<>();
        if(root == null || maximumRows <= 0) return new Snapshot(List.of(), false);
        boolean complete = append(rows, root, List.of(), "", maximumRows, 0);
        return new Snapshot(List.copyOf(rows), !complete);
    }

    // Identify collections without exposing an image's transport metadata as JSON children
    public static boolean isCollection(GraphValue val){
        return val != null && !"image".equals(val.type())
                && (val.value() instanceof Map<?, ?> || val.value() instanceof List<?>);
    }

    // Resolve map keys literally and list indices without splitting their stored path
    public static GraphValue resolve(GraphValue root, List<String> path){
        GraphValue val = root;
        for(String key : path){
            if(!isCollection(val)) return null;
            if(val.value() instanceof Map<?, ?> map){
                Object child = null; boolean found = false;
                for(var entry : map.entrySet()) if(String.valueOf(entry.getKey()).equals(key)){
                    child = entry.getValue(); found = true; break;
                }
                if(!found) return null;
                val = GraphValue.of(child);
            }else{
                List<?> list = (List<?>)val.value();
                try{
                    int idx = Integer.parseInt(key);
                    if(idx < 0 || idx >= list.size()) return null;
                    val = GraphValue.of(list.get(idx));
                }catch(NumberFormatException ignored){ return null; }
            }
        }
        return val;
    }

    // Retain the authored child order and stop before an oversized tree can stall the GUI
    private boolean append(List<Row> rows, GraphValue val, List<String> path, String key, int limit, int depth){
        if(rows.size() >= limit) return false;
        boolean collection = isCollection(val);
        boolean open = collection && expanded.contains(path);
        rows.add(new Row(path, key, val, depth, collection, open));
        if(!open) return true;
        if(depth >= 64) return false;
        if(val.value() instanceof Map<?, ?> map){
            for(var entry : map.entrySet()){
                String name = String.valueOf(entry.getKey());
                if(!append(rows, GraphValue.of(entry.getValue()), childPath(path, name), name, limit, depth + 1)) return false;
            }
        }else if(val.value() instanceof List<?> list){
            for(int idx = 0; idx < list.size(); idx++){
                String name = Integer.toString(idx);
                if(!append(rows, GraphValue.of(list.get(idx)), childPath(path, name), "[" + idx + "]", limit, depth + 1)) return false;
            }
        }
        return true;
    }

    // Append a key without encoding separators into its identity
    private static List<String> childPath(List<String> path, String key){
        List<String> res = new ArrayList<>(path); res.add(key); return List.copyOf(res);
    }

    // Describe one visible row and its current expansion state
    public record Row(List<String> path, String key, GraphValue value, int depth, boolean collection, boolean expanded){
        // Own the row's immutable identity independently of traversal buffers
        public Row{ path = List.copyOf(path); }
    }

    // Mark capped outlines so the host never presents partial data as a complete tree
    public record Snapshot(List<Row> rows, boolean truncated){}
}
