package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDataValue;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDataTables;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Bound primitive values and tables crossing an untrusted script boundary
public final class ScriptValues{
    private ScriptValues(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static Object encode(GraphValue val){
        return copy(val, 0, new int[]{0}, new IdentityHashMap<>());
    }

    public static Map<String, GraphValue> outputs(Map<?, ?> table, Map<String, String> schema){
        Object copied = copy(table, 0, new int[]{0}, new IdentityHashMap<>());
        Map<?, ?> vals = (Map<?, ?>)copied;
        Map<String, GraphValue> res = new LinkedHashMap<>();
        schema.forEach((port, type) -> {
            Object val = vals.get(port);
            if(val == null) val = defaultValue(type);
            if("list".equals(type) && val instanceof Map<?, ?> map) val = array(map);
            val = normalize(val, !"any".equals(type));
            boolean valid = switch(type){
                case "number", "color" -> val instanceof Number;
                case "boolean" -> val instanceof Boolean;
                case "string", "direction" -> val instanceof String;
                case "list" -> val instanceof List<?>;
                case "map", "target", "frequency", "player" -> val instanceof Map<?, ?>;
                default -> true;
            };
            if(!valid) throw new IllegalArgumentException("Output '" + port + "' must be " + type);
            res.put(port, switch(type){
                case "any" -> inferred(val);
                case "color" -> GraphValue.number((int)((Number)val).longValue());
                case "target", "frequency" -> {
                    Map<Object, Object> tagged = new LinkedHashMap<>((Map<?, ?>)val);
                    tagged.remove("__gng_type");
                    var data = GraphDataTables.decode(tagged);
                    if(!(data instanceof GraphDataValue.CompoundValue)) throw new IllegalArgumentException("Output '" + port + "' requires compound graph data");
                    yield new GraphValue(type, data);
                }
                default -> new GraphValue(type, val);
            });
        });
        return Map.copyOf(res);
    }

    // Recover semantic references that passed through a generic Lua table
    private static GraphValue inferred(Object val){
        if(val instanceof Map<?, ?> map && map.get("__gng_type") instanceof String type){
            Map<Object, Object> data = new LinkedHashMap<>(map);
            data.remove("__gng_type");
            if(List.of("target", "frequency").contains(type)){
                var decoded = GraphDataTables.decode(data);
                if(!(decoded instanceof GraphDataValue.CompoundValue)) throw new IllegalArgumentException("Script reference requires compound graph data");
                return new GraphValue(type, decoded);
            }
            if(List.of("player", "image", "widget").contains(type)) return new GraphValue(type, data);
        }
        return GraphValue.of(val);
    }

    public static Object defaultValue(String type){
        return switch(type){
            case "number", "color" -> 0.0D;
            case "boolean" -> false;
            case "list" -> List.of();
            case "map", "target", "frequency", "player" -> Map.of();
            default -> "";
        };
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static List<Object> array(Map<?, ?> map){
        List<Object> res = new ArrayList<>();
        for(int idx = 1; idx <= map.size(); idx++){
            Object val = map.get((double)idx);
            if(val == null) val = map.get(idx);
            if(val == null) throw new IllegalArgumentException("Lists require consecutive keys starting at 1");
            res.add(val);
        }
        return List.copyOf(res);
    }

    // Infer nested Lua arrays while retaining an explicitly declared root map
    private static Object normalize(Object val, boolean retainMap){
        if(val instanceof List<?> list) return list.stream().map(entry -> normalize(entry, false)).toList();
        if(!(val instanceof Map<?, ?> map)) return val;
        if(!retainMap && !map.isEmpty() && map.keySet().stream().allMatch(key -> key instanceof Number)){
            try{ return normalize(array(map), false); }
            catch(IllegalArgumentException ignored){}
        }
        Map<Object, Object> res = new LinkedHashMap<>();
        map.forEach((key, entry) -> res.put(key, normalize(entry, false)));
        return Map.copyOf(res);
    }

    private static Object copy(Object val, int depth, int[] budget, IdentityHashMap<Object, Boolean> active){
        if(depth > 16 || ++budget[0] > 4096) throw new IllegalArgumentException("Script values exceed the table limit");
        if(val instanceof GraphValue graph){
            Object encoded = copy(graph.value(), depth, budget, active);
            if(encoded instanceof Map<?, ?> map && List.of("target", "frequency", "player", "image", "widget").contains(graph.type())){
                Map<Object, Object> res = new LinkedHashMap<>(map);
                res.put("__gng_type", graph.type());
                return Map.copyOf(res);
            }
            return encoded;
        }
        if(val instanceof GraphDataValue data) return copy(GraphDataTables.encode(data), depth, budget, active);
        if(val == null) return "";
        if(val instanceof Boolean) return val;
        if(val instanceof Number num){
            if(!Double.isFinite(num.doubleValue())) throw new IllegalArgumentException("Script numbers must be finite");
            return num.doubleValue();
        }
        if(val instanceof String str){
            if(str.length() > 32768) throw new IllegalArgumentException("Script text exceeds 32768 characters");
            budget[0] += str.length() / 32;
            return str;
        }
        if(active.put(val, true) != null) throw new IllegalArgumentException("Script tables cannot contain cycles");
        try{
            if(val instanceof Map<?, ?> map){
                Map<Object, Object> res = new LinkedHashMap<>();
                map.forEach((key, entry) -> {
                    if(!(key instanceof String) && !(key instanceof Number)) throw new IllegalArgumentException("Script table keys must be text or numbers");
                    res.put(copy(key, depth + 1, budget, active), copy(entry, depth + 1, budget, active));
                });
                return Map.copyOf(res);
            }
            if(val instanceof List<?> list){
                List<Object> res = new ArrayList<>();
                list.forEach(entry -> res.add(copy(entry, depth + 1, budget, active)));
                return List.copyOf(res);
            }
            throw new IllegalArgumentException("Unsupported script value: " + val.getClass().getSimpleName());
        }finally{ active.remove(val); }
    }

}
