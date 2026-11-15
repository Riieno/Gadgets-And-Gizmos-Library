package com.rieno.gadgetsandgizmos.lib.graph.edit;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Preserve typed graph data through ordinary tables, including exact longs and UUID arrays
public final class GraphDataTables{
    private GraphDataTables(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static Object encode(GraphDataValue val){ return encode(val, 0, new int[]{0}); }
    public static GraphDataValue decode(Object val){ return decode(val, 0, new int[]{0}); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static void bound(int depth, int[] budget){
        if(depth > 16 || ++budget[0] > 4096) throw new IllegalArgumentException("Graph data exceeds the table limit");
    }

    private static Object encode(GraphDataValue val, int depth, int[] budget){
        bound(depth, budget);
        int size = switch(val){
            case GraphDataValue.ListValue list -> list.values().size();
            case GraphDataValue.CompoundValue map -> map.values().size();
            case GraphDataValue.ByteArrayValue list -> list.values().size();
            case GraphDataValue.IntArrayValue list -> list.values().size();
            case GraphDataValue.LongArrayValue list -> list.values().size();
            default -> 0;
        };
        if(size > 4096 - budget[0]) throw new IllegalArgumentException("Graph data exceeds the table limit");
        if(val instanceof GraphDataValue.ByteArrayValue || val instanceof GraphDataValue.IntArrayValue
                || val instanceof GraphDataValue.LongArrayValue) budget[0] += size;
        return switch(val){
            case GraphDataValue.NumericValue num -> Map.of("__nbt", num.kind().name().toLowerCase(Locale.ROOT),
                    "value", num.kind() == GraphDataValue.NumberKind.LONG ? num.value().toString() : num.value());
            case GraphDataValue.StringValue str -> str.value();
            case GraphDataValue.ListValue list -> Map.of("__nbt", "list", "value",
                    list.values().stream().map(entry -> encode(entry, depth + 1, budget)).toList());
            case GraphDataValue.CompoundValue map -> {
                Map<String, Object> res = new LinkedHashMap<>();
                map.values().forEach((key, entry) -> res.put(key, encode(entry, depth + 1, budget)));
                yield Map.of("__nbt", "compound", "value", res);
            }
            case GraphDataValue.ByteArrayValue list -> Map.of("__nbt", "byte_array", "value", list.values());
            case GraphDataValue.IntArrayValue list -> Map.of("__nbt", "int_array", "value", list.values());
            case GraphDataValue.LongArrayValue list -> Map.of("__nbt", "long_array", "value", list.values().stream().map(String::valueOf).toList());
        };
    }

    private static GraphDataValue decode(Object val, int depth, int[] budget){
        bound(depth, budget);
        if(val instanceof String str) return new GraphDataValue.StringValue(str);
        if(val instanceof Boolean bool) return new GraphDataValue.NumericValue(GraphDataValue.NumberKind.BYTE, bool ? 1 : 0);
        if(val instanceof Number num) return numeric("double", num);
        if(val instanceof List<?> list) return list(list, depth, budget);
        if(!(val instanceof Map<?, ?> map)) throw new IllegalArgumentException("Graph data requires primitives or tables");
        if(!map.containsKey("__nbt")){
            if(!map.isEmpty() && map.keySet().stream().allMatch(key -> key instanceof Number)) return list(array(map), depth, budget);
            return compound(map, depth, budget);
        }
        if(map.size() != 2 || !map.containsKey("value")) throw new IllegalArgumentException("Tagged graph data requires __nbt and value");
        String kind = String.valueOf(map.get("__nbt")).toLowerCase(Locale.ROOT);
        Object raw = map.get("value");
        return switch(kind){
            case "byte", "short", "int", "long", "float", "double" -> numeric(kind, raw);
            case "compound" -> {
                if(!(raw instanceof Map<?, ?> values)) throw new IllegalArgumentException("A compound requires named fields");
                yield compound(values, depth + 1, budget);
            }
            case "list" -> list(array(raw), depth + 1, budget);
            case "byte_array" -> new GraphDataValue.ByteArrayValue(primitiveArray(raw, budget).stream().map(entry -> decimal(entry).byteValueExact()).toList());
            case "int_array" -> new GraphDataValue.IntArrayValue(primitiveArray(raw, budget).stream().map(entry -> decimal(entry).intValueExact()).toList());
            case "long_array" -> new GraphDataValue.LongArrayValue(primitiveArray(raw, budget).stream().map(entry -> decimal(entry).longValueExact()).toList());
            default -> throw new IllegalArgumentException("Unknown graph data kind: " + kind);
        };
    }

    private static GraphDataValue.NumericValue numeric(String kind, Object val){
        var numberKind = GraphDataValue.NumberKind.valueOf(kind.toUpperCase(Locale.ROOT));
        Number num = numberKind == GraphDataValue.NumberKind.LONG ? decimal(val).longValueExact()
                : val instanceof Number number ? number : decimal(val);
        if(!Double.isFinite(num.doubleValue())) throw new IllegalArgumentException("Graph data numbers must be finite");
        var res = new GraphDataValue.NumericValue(numberKind, num);
        if(!Double.isFinite(res.value().doubleValue())) throw new IllegalArgumentException("Graph data numbers must be finite");
        return res;
    }

    private static BigDecimal decimal(Object val){
        if(!(val instanceof Number) && !(val instanceof String)) throw new IllegalArgumentException("An exact numeric value is required");
        return new BigDecimal(val.toString());
    }

    private static GraphDataValue.ListValue list(List<?> vals, int depth, int[] budget){
        return new GraphDataValue.ListValue(vals.stream().map(val -> decode(val, depth + 1, budget)).toList());
    }

    private static GraphDataValue.CompoundValue compound(Map<?, ?> vals, int depth, int[] budget){
        Map<String, GraphDataValue> res = new LinkedHashMap<>();
        vals.forEach((key, val) -> {
            if(!(key instanceof String str)) throw new IllegalArgumentException("Graph data fields require text keys");
            res.put(str, decode(val, depth + 1, budget));
        });
        return new GraphDataValue.CompoundValue(res);
    }

    private static List<?> array(Object val){
        if(val instanceof List<?> list){
            if(list.size() > 4096) throw new IllegalArgumentException("Graph data exceeds the table limit");
            return list;
        }
        if(!(val instanceof Map<?, ?> map)) throw new IllegalArgumentException("An array table is required");
        if(map.size() > 4096) throw new IllegalArgumentException("Graph data exceeds the table limit");
        List<Object> res = new ArrayList<>();
        for(int idx = 1; idx <= map.size(); idx++){
            Object entry = map.get((double)idx);
            if(entry == null) entry = map.get(idx);
            if(entry == null) throw new IllegalArgumentException("Array keys must be consecutive starting at 1");
            res.add(entry);
        }
        return res;
    }

    private static List<?> primitiveArray(Object val, int[] budget){
        List<?> vals = array(val);
        budget[0] += vals.size();
        if(budget[0] > 4096) throw new IllegalArgumentException("Graph data exceeds the table limit");
        return vals;
    }
}
