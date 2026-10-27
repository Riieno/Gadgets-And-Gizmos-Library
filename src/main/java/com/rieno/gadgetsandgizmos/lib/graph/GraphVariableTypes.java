package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

// Share variable type choices and preserve scalar and structured defaults
public final class GraphVariableTypes{
    private static final List<String> BASE_OPTIONS = List.of(
            "boolean", "integer", "float", "string", "direction",
            "frequency", "target", "list", "map", "color", "any");

    private GraphVariableTypes(){}

    // Include every published data port while keeping the existing numeric choices
    public static List<String> options(GraphNodeRegistry registry){
        var res = new LinkedHashSet<>(BASE_OPTIONS);
        for(GraphNodeDefinition node : registry.definitions()){
            res.addAll(node.inputs().values());
            res.addAll(node.outputs().values());
        }
        res.removeIf(type -> type == null || type.isBlank() || "exec".equals(type) || "number".equals(type));
        return List.copyOf(res);
    }

    // Accept old numeric type ids without changing their graph port type
    public static String normalize(String type, Collection<String> options){
        if("number".equals(type)) return "float";
        return type != null && options.contains(type) ? type : "boolean";
    }

    // Resolve editor choices to wire types
    public static String graphType(String type){
        return "integer".equals(type) || "float".equals(type) ? "number" : type;
    }

    // Colors use the same numeric payload as existing color nodes
    public static String storageType(String type){
        return "color".equals(type) ? "number" : graphType(type);
    }

    // Provide an empty value with the shape expected by the selected type
    public static Object defaultInput(String type){
        return switch(graphType(type)){
            case "boolean" -> false;
            case "number", "color" -> 0.0D;
            case "string", "direction", "any" -> "";
            default -> new CompoundTag();
        };
    }

    // Read structured values without coercing them to numbers or strings
    public static Object inputValue(String type, CompoundTag payload){
        return switch(graphType(type)){
            case "boolean" -> payload.getBoolean("Value");
            case "number", "color" -> payload.getDouble("Value");
            case "string", "direction" -> payload.getString("Value");
            default -> payload.copy();
        };
    }

    // Serialize a variable literal using the existing graph value envelope
    public static CompoundTag valueTag(String type, Object val){
        CompoundTag payload = new CompoundTag();
        if(val instanceof CompoundTag compound) payload = compound.copy();
        else if(val instanceof Boolean bool) payload.putBoolean("Value", bool);
        else if(val instanceof Number num) payload.putDouble("Value", num.doubleValue());
        else payload.putString("Value", val == null ? "" : String.valueOf(val));
        String storedType = storageType(type);
        if("any".equals(storedType)){
            storedType = val instanceof Boolean ? "boolean" : val instanceof Number ? "number"
                    : val instanceof CompoundTag ? "any" : "string";
        }
        CompoundTag res = new CompoundTag();
        res.putString("Type", storedType);
        res.put("Payload", payload);
        return res;
    }

    // Refresh saved dropdown options without replacing other input menus
    public static void putOptions(CompoundTag data, Collection<String> options){
        CompoundTag inputs = data.getCompound("InputOptions");
        ListTag values = new ListTag();
        options.forEach(type -> values.add(StringTag.valueOf(type)));
        inputs.put("type", values);
        data.put("InputOptions", inputs);
    }
}
