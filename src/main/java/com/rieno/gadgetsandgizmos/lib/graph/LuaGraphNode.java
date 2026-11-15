package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

// Describe a host-supplied asynchronous Lua engine with dynamic data ports
public final class LuaGraphNode{
    public static final String SOURCE = "LuaSource";
    public static final String INSTANCE = "cc_tweaked_instance";
    public static final String DEFAULT_SOURCE = "output.result = ports.value\n";

    private LuaGraphNode(){}

    public static GraphNodeDefinition definition(String namespace){
        return new GraphNodeDefinition(ResourceLocation.fromNamespaceAndPath(namespace, "lua").toString(), "async",
                GraphPortOrder.ports("name", "string"),
                GraphPortOrder.ports("valid", "boolean", "error", "string", "busy", "boolean", INSTANCE, "target"), true);
    }

    // Resolve persistent data and execution schemas without implicit execution sockets
    public static Map<String, String> ports(CompoundTag data, boolean output){
        var base = output ? GraphPortOrder.ports("valid", "boolean", "error", "string", "busy", "boolean", INSTANCE, "target") : GraphPortOrder.ports("name", "string");
        var ports = new LinkedHashMap<>(GraphPortOrder.merge(base, ScriptNodePorts.inferred(data.getCompound(output ? "DynamicOutputs" : "DynamicInputs")),
                GraphPortOrder.read(data, output ? GraphPortOrder.OUTPUT_ORDER : GraphPortOrder.INPUT_ORDER)));
        var execution = data.getCompound(output ? com.rieno.gadgetsandgizmos.lib.script.ScriptPorts.EXEC_OUTPUTS : com.rieno.gadgetsandgizmos.lib.script.ScriptPorts.EXEC_INPUTS);
        for(String name : new java.util.TreeSet<>(execution.getAllKeys())) ports.putIfAbsent(name, "exec");
        return GraphPortOrder.ordered(ports, GraphPortOrder.read(data, output ? GraphPortOrder.OUTPUT_ORDER : GraphPortOrder.INPUT_ORDER));
    }

    // Reject reserved or oversized schemas before starting an optional engine
    public static String validation(CompoundTag data){
        if(data.getString(SOURCE).length() > com.rieno.gadgetsandgizmos.lib.script.LuaScripts.MAX_SOURCE_LENGTH)
            return "Script exceeds 32768 characters";
        for(boolean output : List.of(false, true)){
            CompoundTag ports = data.getCompound(output ? "DynamicOutputs" : "DynamicInputs");
            if(ports.size() > 64 - (output ? 4 : 1)) return "Scripts support at most 64 ports per side";
            for(String port : ports.getAllKeys()){
                if(port.isBlank() || port.length() > 64 || port.chars().anyMatch(Character::isISOControl)) return "Invalid script port name";
                if((output ? Set.of("valid", "error", "busy", INSTANCE) : Set.of("name")).contains(port))
                    return "Script port '" + port + "' is reserved";
                if(!ScriptNodePorts.TYPES.contains(ports.getString(port))) return "Unsupported type for script port '" + port + "'";
            }
            if(ports(data, output).size() > 64) return "Scripts support at most 64 ports per side";
        }
        return com.rieno.gadgetsandgizmos.lib.script.ScriptExecPorts.validate(
                new com.rieno.gadgetsandgizmos.lib.script.ScriptExecPorts.Ports(
                        List.copyOf(data.getCompound(com.rieno.gadgetsandgizmos.lib.script.ScriptPorts.EXEC_INPUTS).getAllKeys()),
                        List.copyOf(data.getCompound(com.rieno.gadgetsandgizmos.lib.script.ScriptPorts.EXEC_OUTPUTS).getAllKeys())),
                data.getCompound("DynamicInputs").getAllKeys(), data.getCompound("DynamicOutputs").getAllKeys());
    }
}
