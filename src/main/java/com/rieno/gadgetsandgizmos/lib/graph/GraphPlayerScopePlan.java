package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

// Find personal UI dependencies and their execution paths without host node identifiers
public record GraphPlayerScopePlan(Set<String> nodes, Set<String> variables){
    public GraphPlayerScopePlan{ nodes = Set.copyOf(nodes); variables = Set.copyOf(variables); }
    public static <N extends GraphModel.Node, E extends GraphModel.Edge> GraphPlayerScopePlan analyze(
            GraphModel<N, E> graph, Predicate<N> ui, Predicate<N> action,
            Function<N, String> readVariable, Function<N, String> writeVariable, Predicate<E> execution){
        Map<String, N> nodes = new HashMap<>();
        Map<String, List<String>> dataInputs = new HashMap<>();
        Map<String, List<String>> execInputs = new HashMap<>();
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
        for(E edge : graph.edges()){
            var inputs = execution.test(edge) ? execInputs : dataInputs;
            inputs.computeIfAbsent(edge.toNode(), ignored -> new java.util.ArrayList<>()).add(edge.fromNode());
        }
        Set<String> personal = new HashSet<>();
        Set<String> variables = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        graph.nodes().stream().filter(node -> ui.test(node) || action.test(node)).forEach(node -> pending.add(node.id()));
        while(!pending.isEmpty()){
            String id = pending.removeFirst();
            if(!personal.add(id)) continue;
            N node = nodes.get(id);
            if(node != null){
                String variable = readVariable.apply(node);
                if(variable != null && !variable.isBlank() && variables.add(variable)){
                    graph.nodes().stream().filter(writer -> variable.equals(writeVariable.apply(writer)))
                            .forEach(writer -> pending.add(writer.id()));
                }
            }
            pending.addAll(dataInputs.getOrDefault(id, List.of()));
            pending.addAll(execInputs.getOrDefault(id, List.of()));
        }
        return new GraphPlayerScopePlan(personal, variables);
    }
    // Recognize a host's node-prefixed and variable-prefixed runtime state keys
    public boolean containsStateKey(String key){
        if(key.startsWith("variable:")) return variables.contains(key.substring(9));
        for(String node : nodes) if(key.startsWith(node + ":")) return true;
        return false;
    }
}
