package com.rieno.gadgetsandgizmos.lib.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

// Round-trip authored scalar and structured values without losing zero, false or entry order
public final class GraphValueText{
    private GraphValueText(){}

    public static GraphValue parse(String type, String text){
        String raw = text == null ? "" : text;
        if("string".equals(type) || "direction".equals(type)) return new GraphValue(type, raw);
        if("number".equals(type)){
            double number = Double.parseDouble(raw.strip());
            if(!Double.isFinite(number)) throw new IllegalArgumentException("A finite number is required");
            return GraphValue.number(number);
        }
        if("boolean".equals(type)){
            if(!"true".equalsIgnoreCase(raw.strip()) && !"false".equalsIgnoreCase(raw.strip()))
                throw new IllegalArgumentException("A boolean is required");
            return GraphValue.bool(Boolean.parseBoolean(raw.strip()));
        }
        GraphValue value;
        try{ value = value(JsonParser.parseString(raw)); }
        catch(RuntimeException err){
            if(!"any".equals(type)) throw new IllegalArgumentException("A JSON " + type + " is required", err);
            return GraphValue.string(raw);
        }
        if(!"any".equals(type) && !type.equals(value.type())) throw new IllegalArgumentException("A " + type + " is required");
        return value;
    }

    public static String format(GraphValue value){
        if("string".equals(value.type()) || "direction".equals(value.type())) return value.asString();
        return json(value).toString();
    }

    private static GraphValue value(JsonElement element){
        if(element.isJsonNull()) return GraphValue.string("");
        if(element.isJsonObject()){
            Map<String, GraphValue> entries = new LinkedHashMap<>();
            element.getAsJsonObject().entrySet().forEach(entry -> entries.put(entry.getKey(), value(entry.getValue())));
            return GraphValue.map(entries);
        }
        if(element.isJsonArray()){
            var entries = new ArrayList<GraphValue>();
            element.getAsJsonArray().forEach(entry -> entries.add(value(entry)));
            return GraphValue.list(entries);
        }
        var primitive = element.getAsJsonPrimitive();
        if(primitive.isBoolean()) return GraphValue.bool(primitive.getAsBoolean());
        if(primitive.isNumber()){
            double number = primitive.getAsDouble();
            if(!Double.isFinite(number)) throw new IllegalArgumentException("A finite number is required");
            return GraphValue.number(number);
        }
        return GraphValue.string(primitive.getAsString());
    }

    private static JsonElement json(GraphValue value){
        if(value.value() instanceof Map<?, ?> map){
            var object = new JsonObject();
            map.forEach((key, entry) -> object.add(String.valueOf(key), json(GraphValue.of(entry))));
            return object;
        }
        if(value.value() instanceof Iterable<?> entries){
            var array = new JsonArray();
            entries.forEach(entry -> array.add(json(GraphValue.of(entry))));
            return array;
        }
        if(value.value() instanceof Boolean bool) return new JsonPrimitive(bool);
        if(value.value() instanceof Number number) return new JsonPrimitive(number);
        return new JsonPrimitive(value.asString());
    }
}
