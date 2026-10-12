package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

// Identify nodes whose retained values still describe the editor's current graph
public final class GraphPreviewPlan{
    private final Set<String> unchanged;

    private GraphPreviewPlan(Set<String> unchanged){ this.unchanged = Set.copyOf(unchanged); }

    // The host compares runtime configuration independently of positions, labels and editor metadata
    public static <N extends GraphModel.Node, E extends GraphModel.Edge> GraphPreviewPlan compile(
            GraphModel<N, E> source, GraphModel<N, E> draft, BiPredicate<N, N> sameNode){
        Map<String, N> sources = new HashMap<>();
        source.nodes().forEach(node -> sources.put(node.id(), node));
        Map<String, Set<Wire>> oldInputs = inputs(source);
        Map<String, Set<Wire>> newInputs = inputs(draft);
        Map<String, Set<String>> consumers = new HashMap<>();
        for(E edge : source.edges()) consumers.computeIfAbsent(edge.fromNode(), ignored -> new HashSet<>()).add(edge.toNode());
        for(E edge : draft.edges()) consumers.computeIfAbsent(edge.fromNode(), ignored -> new HashSet<>()).add(edge.toNode());
        Set<String> unchanged = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> currentIds = new HashSet<>();
        draft.nodes().forEach(node -> currentIds.add(node.id()));
        sources.keySet().stream().filter(id -> !currentIds.contains(id)).forEach(pending::add);
        for(N node : draft.nodes()){
            N prev = sources.get(node.id());
            if(prev != null && sameNode.test(prev, node)
                    && oldInputs.getOrDefault(node.id(), Set.of()).equals(newInputs.getOrDefault(node.id(), Set.of()))){
                unchanged.add(node.id());
            }else{
                pending.add(node.id());
            }
        }
        Set<String> visited = new HashSet<>();
        while(!pending.isEmpty()){
            String id = pending.removeFirst();
            if(!visited.add(id)) continue;
            unchanged.remove(id);
            pending.addAll(consumers.getOrDefault(id, Set.of()));
        }
        return new GraphPreviewPlan(unchanged);
    }

    public boolean unchanged(String nodeId){ return unchanged.contains(nodeId); }

    private static <N extends GraphModel.Node, E extends GraphModel.Edge> Map<String, Set<Wire>> inputs(GraphModel<N, E> graph){
        Map<String, Set<Wire>> res = new HashMap<>();
        for(E edge : graph.edges()){
            res.computeIfAbsent(edge.toNode(), ignored -> new HashSet<>()).add(
                    new Wire(edge.fromNode(), edge.fromPort(), edge.toPort()));
        }
        return res;
    }

    private record Wire(String node, String fromPort, String toPort){}
}
