package com.rieno.gadgetsandgizmos.lib.worker;

import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Allocate overlapping ingredient tags by capacity rather than trying every item distribution
public final class WorkerIngredientAllocation{
    private WorkerIngredientAllocation(){}

    // Return exact reservations for each slot, or null when the shared stock cannot cover every slot
    public static List<Map<WorkerResourceKey, Long>> allocate(List<WorkerRecipeDefinition.Ingredient> ingredients,
                                                              List<Long> amounts,
                                                              Map<WorkerResourceKey, Long> available){
        if(ingredients.size() != amounts.size()) throw new IllegalArgumentException("Each ingredient needs an amount");
        Map<WorkerResourceKey, Integer> resources = new LinkedHashMap<>();
        for(var ingredient : ingredients){
            DeferredWorkScheduler.checkpoint();
            for(WorkerResourceKey resource : ingredient.alternatives()){
                if(available.getOrDefault(resource, 0L) > 0L)
                    resources.computeIfAbsent(resource, key -> resources.size() + 1);
            }
        }
        int firstSlot = resources.size() + 1;
        int sink = firstSlot + ingredients.size();
        List<List<Edge>> edges = new ArrayList<>();
        for(int idx = 0; idx <= sink; idx++) edges.add(new ArrayList<>());
        resources.forEach((resource, idx) -> link(edges, 0, idx, available.get(resource)));
        List<Map<WorkerResourceKey, Edge>> reservations = new ArrayList<>();
        List<Edge> demands = new ArrayList<>();
        for(int idx = 0; idx < ingredients.size(); idx++){
            long amount = Math.max(0L, amounts.get(idx));
            demands.add(link(edges, firstSlot + idx, sink, amount));
            Map<WorkerResourceKey, Edge> inputs = new LinkedHashMap<>();
            for(WorkerResourceKey resource : ingredients.get(idx).alternatives()){
                Integer node = resources.get(resource);
                if(node != null && amount > 0L) inputs.put(resource, link(edges, node, firstSlot + idx, amount));
            }
            reservations.add(inputs);
        }
        int[] levels = new int[edges.size()];
        while(levels(edges, sink, levels)){
            int[] next = new int[edges.size()];
            while(send(edges, 0, sink, Long.MAX_VALUE, levels, next) > 0L) DeferredWorkScheduler.checkpoint();
        }
        if(demands.stream().anyMatch(edge -> edge.capacity > 0L)) return null;
        List<Map<WorkerResourceKey, Long>> res = new ArrayList<>();
        for(var slot : reservations){
            Map<WorkerResourceKey, Long> selected = new LinkedHashMap<>();
            slot.forEach((resource, edge) -> {
                long amount = edges.get(edge.to).get(edge.reverse).capacity;
                if(amount > 0L) selected.put(resource, amount);
            });
            res.add(Map.copyOf(selected));
        }
        return List.copyOf(res);
    }

    private static Edge link(List<List<Edge>> edges, int from, int to, long amount){
        Edge forward = new Edge(to, edges.get(to).size(), amount);
        Edge reverse = new Edge(from, edges.get(from).size(), 0L);
        edges.get(from).add(forward);
        edges.get(to).add(reverse);
        return forward;
    }

    private static boolean levels(List<List<Edge>> edges, int sink, int[] levels){
        Arrays.fill(levels, -1);
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(0);
        levels[0] = 0;
        while(!pending.isEmpty()){
            DeferredWorkScheduler.checkpoint();
            int node = pending.removeFirst();
            for(Edge edge : edges.get(node)){
                if(edge.capacity <= 0L || levels[edge.to] >= 0) continue;
                levels[edge.to] = levels[node] + 1;
                pending.addLast(edge.to);
            }
        }
        return levels[sink] >= 0;
    }

    private static long send(List<List<Edge>> edges, int node, int sink, long amount, int[] levels, int[] next){
        if(node == sink) return amount;
        for(; next[node] < edges.get(node).size(); next[node]++){
            Edge edge = edges.get(node).get(next[node]);
            if(edge.capacity <= 0L || levels[edge.to] != levels[node] + 1) continue;
            long sent = send(edges, edge.to, sink, Math.min(amount, edge.capacity), levels, next);
            if(sent <= 0L) continue;
            edge.capacity -= sent;
            edges.get(edge.to).get(edge.reverse).capacity += sent;
            return sent;
        }
        return 0L;
    }

    private static final class Edge{
        private final int to;
        private final int reverse;
        private long capacity;
        private Edge(int to, int reverse, long capacity){
            this.to = to;
            this.reverse = reverse;
            this.capacity = capacity;
        }
    }
}
