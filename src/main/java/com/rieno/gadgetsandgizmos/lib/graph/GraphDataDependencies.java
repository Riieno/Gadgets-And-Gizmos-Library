package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

// Find data outputs that need reevaluation after a retained value changes
public final class GraphDataDependencies{
    private final Map<Port, Set<String>> ports;
    private final Map<String, Set<String>> nodes;
    private final Map<Port, Set<String>> affected = new HashMap<>();

    private GraphDataDependencies(Map<Port, Set<String>> ports, Map<String, Set<String>> nodes){
        this.ports = ports;
        this.nodes = nodes;
    }

    public static GraphDataDependencies empty(){ return new GraphDataDependencies(Map.of(), Map.of()); }

    // Capture host-selected data edges independently of execution wiring
    public static <N extends GraphModel.Node, E extends GraphModel.Edge> GraphDataDependencies compile(
            GraphModel<N, E> graph, Predicate<E> dataEdge){
        Map<Port, Set<String>> ports = new HashMap<>();
        Map<String, Set<String>> nodes = new HashMap<>();
        for(E edge : graph.edges()){
            if(!dataEdge.test(edge)) continue;
            ports.computeIfAbsent(new Port(edge.fromNode(), edge.fromPort()), ignored -> new HashSet<>()).add(edge.toNode());
            nodes.computeIfAbsent(edge.fromNode(), ignored -> new HashSet<>()).add(edge.toNode());
        }
        return new GraphDataDependencies(ports, nodes);
    }

    // Include direct and transitive consumers of one changed data port, stopping at cycles
    public Set<String> affectedNodes(String nodeId, String port){
        return affected.computeIfAbsent(new Port(nodeId, port), key -> {
            Set<String> res = new HashSet<>();
            ArrayDeque<String> pending = new ArrayDeque<>(ports.getOrDefault(key, Set.of()));
            while(!pending.isEmpty()){
                String node = pending.removeFirst();
                if(res.add(node)) pending.addAll(nodes.getOrDefault(node, Set.of()));
            }
            return Set.copyOf(res);
        });
    }

    private record Port(String node, String port){}
}
