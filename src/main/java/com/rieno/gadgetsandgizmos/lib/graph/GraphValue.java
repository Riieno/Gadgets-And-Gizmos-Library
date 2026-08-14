package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Carry a value between reusable graph nodes without depending on the game runtime
public record GraphValue(String type, Object value) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph value
    public GraphValue {
        type = type == null || type.isBlank() ? "any" : type;
        value = immutableValue(value, new IdentityHashMap<>());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the numeric value
    public static GraphValue number(double value) {
        return new GraphValue("number", Double.isFinite(value) ? value : 0.0D);
    }

    // Create a boolean graph value
    public static GraphValue bool(boolean value) {
        return new GraphValue("boolean", value);
    }

    // Create a text graph value
    public static GraphValue string(String value) {
        return new GraphValue("string", value == null ? "" : value);
    }

    // Get the list
    public static GraphValue list(List<?> value) {
        return new GraphValue("list", value == null ? List.of() : value);
    }

    // Map the graph value
    public static GraphValue map(Map<?, ?> value) {
        return new GraphValue("map", value == null ? Map.of() : value);
    }

    // Get the graph value as number
    public double asNumber() {
        if (value instanceof Number num) return num.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    // Get the graph value as boolean
    public boolean asBoolean() {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    // Get the graph value as string
    public String asString() {
        return value == null ? "" : String.valueOf(value);
    }

    // Get the immutable value
    private static Object immutableValue(Object val, IdentityHashMap<Object, Boolean> active) {
        if (val instanceof List<?> list) {
            enter(val, active);
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(entry -> copy.add(immutableValue(entry, active)));
            active.remove(val);
            return List.copyOf(copy);
        }
        if (val instanceof Map<?, ?> map) {
            enter(val, active);
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(
                    immutableValue(key, active), immutableValue(entry, active)));
            active.remove(val);
            return Map.copyOf(copy);
        }
        if (val != null && val.getClass().isArray()) {
            throw new IllegalArgumentException("Graph values cannot contain arrays");
        }
        if (val instanceof Set<?> set) {
            enter(val, active);
            Set<Object> copy = new java.util.LinkedHashSet<>();
            set.forEach(entry -> copy.add(immutableValue(entry, active)));
            active.remove(val);
            return Set.copyOf(copy);
        }
        return val;
    }

    // Reject recursive graph values
    private static void enter(Object val, IdentityHashMap<Object, Boolean> active) {
        if (active.put(val, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Graph values cannot contain recursive collections");
        }
    }
}
