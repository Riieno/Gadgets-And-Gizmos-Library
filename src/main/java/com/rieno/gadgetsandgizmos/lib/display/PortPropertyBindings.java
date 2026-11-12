package com.rieno.gadgetsandgizmos.lib.display;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Edit property bindings from the port side without changing their saved representation
public final class PortPropertyBindings{
    private PortPropertyBindings(){}

    // Assign one property independently so several properties can share the same port
    public static Map<String, String> assignProperty(Map<String, String> bindings, String property, String port){
        if(property == null || property.isBlank()) throw new IllegalArgumentException("Binding property must not be blank");
        Map<String, String> res = new LinkedHashMap<>(bindings);
        if(port == null || port.isBlank()) res.remove(property);
        else res.put(property, port);
        return res;
    }

    public static List<String> properties(Map<String, String> bindings, String port){
        if(port == null || port.isBlank()) return List.of();
        return bindings.entrySet().stream().filter(entry -> port.equals(entry.getValue()))
                .map(Map.Entry::getKey).toList();
    }

    // An empty property restores this port's static defaults and preserves other ports
    public static Map<String, String> assign(Map<String, String> bindings, String port, String property){
        if(port == null || port.isBlank()) throw new IllegalArgumentException("Binding port must not be blank");
        Map<String, String> res = new LinkedHashMap<>(bindings);
        res.entrySet().removeIf(entry -> port.equals(entry.getValue()));
        if(property != null && !property.isBlank()) res.put(property, port);
        return res;
    }
}
