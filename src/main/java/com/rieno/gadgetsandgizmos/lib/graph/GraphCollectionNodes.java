package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Supply collection schemas and executors under the consuming mod's namespace
public final class GraphCollectionNodes{
    private GraphCollectionNodes(){}

    public record Node(GraphNodeDefinition definition, GraphNodeExecutor executor, Map<String, GraphValue> defaults){
        public Node{ defaults = GraphPortOrder.copy(defaults); }
    }

    public static List<Node> nodes(String namespace){
        if(namespace == null || ResourceLocation.tryBuild(namespace, "collection_push") == null){
            throw new IllegalArgumentException("Collection nodes require an owning namespace");
        }
        List<Node> nodes = new ArrayList<>();
        for(String operation : List.of("push", "pop", "contains", "index_of")){
            Map<String, String> inputs = new LinkedHashMap<>();inputs.put("collection", "any");
            Map<String, String> outputs = new LinkedHashMap<>();
            Map<String, GraphValue> defaults = new LinkedHashMap<>();
            defaults.put("collection", GraphValue.list(List.of()));
            if("pop".equals(operation)){
                inputs.put("index", "number");defaults.put("index", GraphValue.number(0));
                outputs.put("result", "any");outputs.put("value", "any");outputs.put("removed", "boolean");
            }else{
                inputs.put("value", "any");defaults.put("value", GraphValue.number(0));
                if("push".equals(operation)){
                    inputs.put("key", "string");defaults.put("key", GraphValue.string("key"));outputs.put("result", "any");
                }else outputs.put("contains".equals(operation) ? "result" : "index",
                        "contains".equals(operation) ? "boolean" : "number");
            }
            GraphNodeDefinition definition = new GraphNodeDefinition(namespace + ":collection_" + operation,
                    "list_map", inputs, outputs, false);
            nodes.add(new Node(definition, (context, values) -> execute(operation, values), defaults));
        }
        return List.copyOf(nodes);
    }

    private static Map<String, GraphValue> execute(String operation, Map<String, GraphValue> inputs){
        GraphValue collection = inputs.getOrDefault("collection", GraphValue.list(List.of()));
        GraphValue value = inputs.getOrDefault("value", GraphValue.number(0));
        return switch(operation){
            case "push" -> Map.of("result", GraphCollectionOperations.push(collection, value,
                    inputs.getOrDefault("key", GraphValue.string("key")).asString()));
            case "pop" -> {
                var popped = GraphCollectionOperations.pop(collection, inputs.getOrDefault("index", GraphValue.number(0)).asNumber());
                yield GraphPortOrder.entries(Map.entry("result", popped.result()), Map.entry("value", popped.value()),
                        Map.entry("removed", GraphValue.bool(popped.removed())));
            }
            case "contains" -> Map.of("result", GraphValue.bool(GraphCollectionOperations.contains(collection, value)));
            case "index_of" -> Map.of("index", GraphValue.number(GraphCollectionOperations.indexOf(collection, value)));
            default -> throw new IllegalArgumentException("Unknown collection operation: " + operation);
        };
    }
}
