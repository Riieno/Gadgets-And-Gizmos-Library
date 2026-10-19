package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.function.BiFunction;

// Format graph numbers independently of their stored value and calculation precision
public final class GraphNumberDisplay{
    public static final String PRECISE_VALUES_TAG = "EditorShowPreciseValues";
    public static final String PRECISE_WIDTH_SAMPLE = "-1.2345678901234567E-308";

    private GraphNumberDisplay(){}

    public static boolean precise(CompoundTag data){
        return data != null && data.getBoolean(PRECISE_VALUES_TAG);
    }

    public static void setPrecise(CompoundTag data, boolean precise){
        if(precise) data.putBoolean(PRECISE_VALUES_TAG, true);
        else data.remove(PRECISE_VALUES_TAG);
    }

    public static boolean numericType(String type){
        return "number".equals(type) || "integer".equals(type) || "int".equals(type)
                || "float".equals(type) || "double".equals(type);
    }

    // Precise text round-trips the stored double; compact text preserves the existing editor format
    public static String format(double value, boolean precise){
        if(precise) return Double.toString(value);
        return Math.abs(value - Math.rint(value)) < 0.0001 ? Long.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    // Reserve display controls for ports whose runtime type can become numeric
    public static boolean supportsNumericPorts(Map<String, String> inputs, Map<String, String> outputs){
        return java.util.stream.Stream.concat(inputs.values().stream(), outputs.values().stream())
                .anyMatch(type -> numericType(type) || "any".equals(type));
    }

    // Ask the host to resolve only ports whose declared type remains any
    public static boolean hasNumericPorts(Map<String, String> inputs, Map<String, String> outputs,
                                           BiFunction<String, Boolean, String> resolvedType){
        for(boolean output : new boolean[]{false, true}){
            for(var port : (output ? outputs : inputs).entrySet()){
                if(numericType(port.getValue()) || "any".equals(port.getValue())
                        && numericType(resolvedType.apply(port.getKey(), output))) return true;
            }
        }
        return false;
    }
}
