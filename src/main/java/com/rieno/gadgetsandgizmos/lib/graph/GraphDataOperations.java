package com.rieno.gadgetsandgizmos.lib.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

// Inspect graph collections and decode serialized values without depending on a host node
public final class GraphDataOperations{
    private GraphDataOperations(){}

    // Count top-level entries or string characters; unsupported values have no length
    public static int length(GraphValue val){
        if(val == null) return 0;
        if(val.value() instanceof String text) return text.length();
        if(val.value() instanceof List<?> list) return list.size();
        if(val.value() instanceof Map<?, ?> map) return map.size();
        return 0;
    }

    // Decode JSON strings and join nested list/map values, keeping invalid input intact
    public static String stripJson(String text){
        if(text == null) return "";
        if(text.isBlank()) return text;
        JsonElement root;
        try{
            root = JsonParser.parseString(text);
        }catch(RuntimeException ignored){
            String repaired = repairNumberedKeys(text);
            if(repaired.equals(text)) return text;
            try{ root = JsonParser.parseString(repaired); }
            catch(RuntimeException invalid){ return text; }
        }
        String trimmed = text.strip();
        if(root.isJsonPrimitive() && root.getAsJsonPrimitive().isString()
                && !trimmed.startsWith("\"") && !trimmed.startsWith("'")) return text;
        StringJoiner result = new StringJoiner(" ");
        Deque<JsonElement> pending = new ArrayDeque<>();
        pending.push(root);
        while(!pending.isEmpty()){
            JsonElement val = pending.pop();
            if(val.isJsonNull()) continue;
            if(val.isJsonPrimitive()){
                String decoded = val.getAsString();
                if(!decoded.isEmpty()) result.add(decoded);
            }else if(val.isJsonArray()){
                var array = val.getAsJsonArray();
                for(int idx = array.size() - 1; idx >= 0; idx--) pending.push(array.get(idx));
            }else{
                JsonObject object = val.getAsJsonObject();
                JsonElement payload = graphPayload(object);
                if(payload != null){
                    pending.push(payload);
                    continue;
                }
                var entries = new ArrayList<>(object.entrySet());
                if(entries.stream().allMatch(entry -> entry.getKey().matches("[0-9]+"))){
                    entries.sort((a, b) -> new BigInteger(a.getKey()).compareTo(new BigInteger(b.getKey())));
                }
                for(int idx = entries.size() - 1; idx >= 0; idx--) pending.push(entries.get(idx).getValue());
            }
        }
        return result.toString();
    }

    // Recognize the typed value wrappers used when graph lists/maps are serialized
    private static JsonElement graphPayload(JsonObject object){
        if(object.size() != 2 || !object.has("Type") || !object.has("Payload")) return null;
        JsonElement type = object.get("Type");
        if(!type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString()
                || !object.get("Payload").isJsonObject()) return null;
        JsonObject payload = object.getAsJsonObject("Payload");
        return switch(type.getAsString()){
            case "string", "direction", "number", "boolean", "color" ->
                    payload.size() == 1 && payload.has("Value") ? payload.get("Value") : null;
            case "list", "map" -> payload;
            default -> null;
        };
    }

    // Accept a missing colon between a numeric object key and a quoted value
    private static String repairNumberedKeys(String text){
        StringBuilder result = new StringBuilder(text.length());
        Deque<Character> containers = new ArrayDeque<>();
        char quote = 0;
        boolean escaped = false;
        for(int idx = 0; idx < text.length(); idx++){
            char ch = text.charAt(idx);
            result.append(ch);
            if(quote != 0){
                if(escaped) escaped = false;
                else if(ch == '\\') escaped = true;
                else if(ch == quote) quote = 0;
                continue;
            }
            if(ch == '"' || ch == '\''){ quote = ch; continue; }
            if(ch == '{' || ch == '[') containers.push(ch);
            else if(ch == '}' || ch == ']'){
                if(!containers.isEmpty()) containers.pop();
            }
            if((ch != '{' && ch != ',') || containers.isEmpty() || containers.peek() != '{') continue;
            int start = idx + 1;
            while(start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
            int end = start;
            while(end < text.length() && text.charAt(end) >= '0' && text.charAt(end) <= '9') end++;
            if(end == start) continue;
            while(end < text.length() && Character.isWhitespace(text.charAt(end))) end++;
            if(end >= text.length() || (text.charAt(end) != '"' && text.charAt(end) != '\'')) continue;
            result.append(text, idx + 1, end).append(':');
            idx = end - 1;
        }
        return result.toString();
    }
}
