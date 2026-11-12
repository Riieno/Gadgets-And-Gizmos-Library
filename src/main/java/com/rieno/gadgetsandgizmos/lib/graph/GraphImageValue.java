package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.LinkedHashMap;
import java.util.Map;

// Carry an image source and optional host-owned asset metadata through graph values
public final class GraphImageValue{
    private GraphImageValue(){}

    public static GraphValue of(String source){ return of(source, Map.of()); }

    // Retain detached metadata so embedded images can render without an open graph editor
    public static GraphValue of(String source, Map<String, ?> metadata){
        Map<String, Object> payload = new LinkedHashMap<>(metadata);
        payload.put("Value", source == null ? "" : source);
        return new GraphValue("image", payload);
    }

    public static String source(GraphValue value){
        if(value == null) return "";
        if("image".equals(value.type()) && value.value() instanceof Map<?, ?> payload){
            Object source = payload.get("Value");
            return source == null ? "" : source.toString();
        }
        return "string".equals(value.type()) || "direction".equals(value.type()) ? value.asString() : "";
    }
}
