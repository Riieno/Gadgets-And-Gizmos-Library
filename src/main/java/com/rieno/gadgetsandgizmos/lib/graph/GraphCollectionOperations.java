package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Transform immutable collections and search their values in source order
public final class GraphCollectionOperations{
    private GraphCollectionOperations(){}

    public record Pop(GraphValue result, GraphValue value, boolean removed){}

    // Append a list item or insert a map value under the supplied key
    public static GraphValue push(GraphValue collection, GraphValue value, String key){
        if(collection != null && collection.value() instanceof Map<?, ?> map){
            Map<Object, Object> result = new LinkedHashMap<>(map);
            result.put(key == null ? "" : key, value);
            return GraphValue.map(result);
        }
        List<Object> result = new ArrayList<>();
        if(collection != null && collection.value() instanceof List<?> list) result.addAll(list);
        result.add(value);
        return GraphValue.list(result);
    }

    // Indices are zero based for lists and for a map's ordered values
    public static Pop pop(GraphValue collection, double requestedIndex){
        GraphValue original = collection == null ? GraphValue.list(List.of()) : collection;
        int size = original.value() instanceof Map<?, ?> map ? map.size()
                : original.value() instanceof List<?> list ? list.size() : 0;
        if(!Double.isFinite(requestedIndex) || requestedIndex < 0 || requestedIndex >= size){
            return new Pop(original, new GraphValue("any", null), false);
        }
        int index = (int)Math.floor(requestedIndex);
        if(original.value() instanceof Map<?, ?> map){
            Map<Object, Object> result = new LinkedHashMap<>(map);
            Iterator<Map.Entry<Object, Object>> entries = result.entrySet().iterator();
            for(int idx = 0; idx < index; idx++) entries.next();
            GraphValue value = GraphValue.of(entries.next().getValue());
            entries.remove();
            return new Pop(GraphValue.map(result), value, true);
        }
        List<Object> result = new ArrayList<>((List<?>)original.value());
        GraphValue value = GraphValue.of(result.remove(index));
        return new Pop(GraphValue.list(result), value, true);
    }

    public static boolean contains(GraphValue collection, GraphValue value){
        return indexOf(collection, value) >= 0;
    }

    // Map searches compare values rather than keys and report the first matching position
    public static int indexOf(GraphValue collection, GraphValue value){
        if(collection == null) return -1;
        Iterable<?> values = collection.value() instanceof Map<?, ?> map ? map.values()
                : collection.value() instanceof List<?> list ? list : List.of();
        int index = 0;
        for(Object entry : values){
            if(equal(GraphValue.of(entry), value)) return index;
            index++;
        }
        return -1;
    }

    private static boolean equal(GraphValue a, GraphValue b){
        if(a == null || b == null) return a == b;
        if(!a.type().equals(b.type())) return false;
        if("number".equals(a.type())) return a.asNumber() == b.asNumber();
        if(a.value() instanceof List<?> left && b.value() instanceof List<?> right){
            if(left.size() != right.size()) return false;
            for(int idx = 0; idx < left.size(); idx++) if(!equal(GraphValue.of(left.get(idx)), GraphValue.of(right.get(idx)))) return false;
            return true;
        }
        if(a.value() instanceof Map<?, ?> left && b.value() instanceof Map<?, ?> right){
            if(!left.keySet().equals(right.keySet())) return false;
            for(Object key : left.keySet()) if(!equal(GraphValue.of(left.get(key)), GraphValue.of(right.get(key)))) return false;
            return true;
        }
        return java.util.Objects.equals(a.value(), b.value());
    }
}
