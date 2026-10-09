package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.function.Function;

// Configure ordered text inputs while retaining legacy two-input graphs
public final class GraphTextInputs {
    public static final String CONFIGURED_TAG = "TextInputsConfigured";
    public static final int MAX_INPUTS = 32;

    private GraphTextInputs(){
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read legacy inputs or the explicitly configured schema in stable text order
    public static Map<String, String> inputs(CompoundTag data){
        CompoundTag ports = data.getCompound("DynamicInputs");
        Map<String, String> res = new LinkedHashMap<>();
        if(!data.getBoolean(CONFIGURED_TAG)){
            res.put("a", "string");
            res.put("b", "string");
        }
        ports.getAllKeys().stream().sorted(Comparator.comparingInt(GraphTextInputs::ordinal)
                .thenComparing(Function.identity())).forEach(port -> res.put(port, "string"));
        return res;
    }

    // Add a text input without changing existing port ids or values
    public static String add(CompoundTag data){
        Map<String, String> existing = inputs(data);
        if(existing.size() >= MAX_INPUTS) return "";
        CompoundTag ports = new CompoundTag();
        existing.forEach(ports::putString);
        int idx = Math.max(3, existing.keySet().stream().mapToInt(GraphTextInputs::ordinal)
                .filter(val -> val < Integer.MAX_VALUE).max().orElse(2) + 1);
        while(ports.contains("text_" + idx)) idx++;
        String port = "text_" + idx;
        ports.putString(port, "string");
        data.put("DynamicInputs", ports);
        data.putBoolean(CONFIGURED_TAG, true);
        return port;
    }

    // Remove a text input while retaining at least one input
    public static boolean remove(CompoundTag data, String port){
        Map<String, String> existing = inputs(data);
        if(existing.size() <= 1 || !existing.containsKey(port)) return false;
        existing.remove(port);
        CompoundTag ports = new CompoundTag();
        existing.forEach(ports::putString);
        data.put("DynamicInputs", ports);
        data.putBoolean(CONFIGURED_TAG, true);
        for(String key : List.of("Defaults", "InputOptions", "InputLabels")){
            CompoundTag values = data.getCompound(key);
            values.remove(port);
            if(values.isEmpty()) data.remove(key);
            else data.put(key, values);
        }
        return true;
    }

    // Join every configured input in port order
    public static String join(CompoundTag data, Function<String, String> values){
        return compileJoin(data).join(values);
    }

    // Prepare the stable input order when a graph is compiled
    public static CompiledJoin compileJoin(CompoundTag data){
        return new CompiledJoin(List.copyOf(inputs(data).keySet()));
    }

    // Retain port order without retaining mutable graph data
    public record CompiledJoin(List<String> ports){
        public CompiledJoin{
            ports = List.copyOf(ports);
        }

        public String join(Function<String, String> values){
            StringBuilder res = new StringBuilder();
            for(String port : ports){
                String val = values.apply(port);
                if(val != null) res.append(val);
            }
            return res.toString();
        }
    }

    private static int ordinal(String port){
        if("a".equals(port)) return 1;
        if("b".equals(port)) return 2;
        try{
            return Integer.parseInt(port.startsWith("text_") ? port.substring(5) : port);
        }catch(NumberFormatException ignored){
            return Integer.MAX_VALUE;
        }
    }
}
