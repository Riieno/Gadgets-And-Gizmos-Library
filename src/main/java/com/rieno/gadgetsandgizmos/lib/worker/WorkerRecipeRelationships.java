package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

// Share recipe and tag relationships instead of storing every possible crafting tree
public final class WorkerRecipeRelationships{
    private static final int UNREACHABLE = Integer.MAX_VALUE / 4;
    private final Map<WorkerResourceKey, List<Node>> producers = new HashMap<>();
    private final Map<WorkerResourceKey, List<Group>> consumers = new HashMap<>();
    private final Map<Group, List<Node>> uses = new IdentityHashMap<>();
    private static final int MAX_SCOPED_RECIPES = 250_000;
    private final Map<List<WorkerResourceKey>, Scope> scopes = new LinkedHashMap<>(16, 0.75f, true);
    private int scopedRecipes;

    // Intern alternative groups so a large tag has one set of links across all recipes
    public WorkerRecipeRelationships(Collection<WorkerRecipeDefinition> definitions){
        Map<List<WorkerResourceKey>, Group> groups = new HashMap<>();
        Map<List<WorkerResourceKey>, Group> identities = new IdentityHashMap<>();
        for(WorkerRecipeDefinition def : definitions){
            DeferredWorkScheduler.checkpoint();
            List<Group> inputs = new ArrayList<>();
            for(var ingredient : def.ingredients()){
                Group group = identities.get(ingredient.alternatives());
                if(group == null){
                    group = groups.get(ingredient.alternatives());
                    if(group == null){
                        group = new Group(ingredient.alternatives());
                        groups.put(ingredient.alternatives(), group);
                        for(WorkerResourceKey resource : group.alternatives)
                            consumers.computeIfAbsent(resource, key -> new ArrayList<>()).add(group);
                    }
                    identities.put(ingredient.alternatives(), group);
                }
                inputs.add(group);
            }
            Node node = new Node(def, List.copyOf(inputs));
            producers.computeIfAbsent(def.result(), key -> new ArrayList<>()).add(node);
            for(Group group : inputs) uses.computeIfAbsent(group, key -> new ArrayList<>()).add(node);
        }
    }

    // Propagate stock through each related ingredient group once, including cyclic conversion graphs
    public Ranks rank(Collection<WorkerResourceKey> outputs, Map<WorkerResourceKey, Long> stocked,
                      Predicate<WorkerRecipeDefinition> supported, WorkerRecipePlanner.IngredientStock machineStock){
        return rank(outputs, stocked, supported, machineStock, false);
    }

    // Include preloaded alternatives even when storage already seeds a different route to the output
    public Ranks rank(Collection<WorkerResourceKey> outputs, Map<WorkerResourceKey, Long> stocked,
                      Predicate<WorkerRecipeDefinition> supported, WorkerRecipePlanner.IngredientStock machineStock,
                      boolean includePreloadedRoutes){
        return rank(outputs, stocked, supported, machineStock, includePreloadedRoutes, null);
    }

    // Retain one producing witness per reachable resource instead of enumerating dependency combinations
    public Routes routes(Collection<WorkerResourceKey> outputs, Map<WorkerResourceKey, Long> stocked,
                         Predicate<WorkerRecipeDefinition> supported, WorkerRecipePlanner.IngredientStock machineStock,
                         boolean includePreloadedRoutes){
        Map<WorkerResourceKey, WorkerRecipeDefinition> preferred = new HashMap<>();
        Ranks ranks = rank(outputs, stocked, supported, machineStock, includePreloadedRoutes, preferred);
        return new Routes(ranks, Map.copyOf(preferred));
    }

    private Ranks rank(Collection<WorkerResourceKey> outputs, Map<WorkerResourceKey, Long> stocked,
                       Predicate<WorkerRecipeDefinition> supported, WorkerRecipePlanner.IngredientStock machineStock,
                       boolean includePreloadedRoutes, Map<WorkerResourceKey, WorkerRecipeDefinition> preferred){
        Scope scope = scope(outputs);
        Map<WorkerResourceKey, Integer> production = new HashMap<>();
        Map<Group, Integer> costs = new IdentityHashMap<>();
        Map<Node, Progress> progress = new IdentityHashMap<>();
        PriorityQueue<Route> pending = new PriorityQueue<>(Comparator.comparingInt(Route::cost));
        for(Node node : scope.nodes) progress.put(node, new Progress(node.inputs.size()));
        for(var entry : stocked.entrySet()){
            if(entry.getValue() <= 0L) continue;
            for(Group group : scope.consumers.getOrDefault(entry.getKey(), List.of())){
                if(costs.putIfAbsent(group, 0) == null) pending.add(new Route(group, 0));
            }
        }
        Set<Group> settled = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        propagate(scope, pending, costs, settled, progress, production, supported, preferred);
        if(machineStock != null && (includePreloadedRoutes || outputs.stream().anyMatch(output -> !production.containsKey(output)
                && stocked.getOrDefault(output, 0L) <= 0L))){
            preload(scope, outputs, pending, costs, settled, progress, production, supported, machineStock,
                    preferred, includePreloadedRoutes);
        }
        return new Ranks(Map.copyOf(production), scope.nodes.size());
    }

    // Read detached definitions for one output without traversing its ingredients
    public List<WorkerRecipeDefinition> producing(WorkerResourceKey output){
        return producers.getOrDefault(output, List.of()).stream().map(node -> node.def).toList();
    }

    int recipeCount(){ return producers.values().stream().mapToInt(List::size).sum(); }

    // Expand consumers only when a caller asks for the recipes accepting one resource
    public List<WorkerRecipeDefinition> consuming(WorkerResourceKey resource){
        Set<Node> found = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        for(Group group : consumers.getOrDefault(resource, List.of())){
            for(Node node : uses.get(group)) if(found.add(node)) definitions.add(node.def);
        }
        return List.copyOf(definitions);
    }

    // Discover each resource once for sources that do not supply a prebuilt relationship map
    static WorkerRecipeRelationships discover(WorkerRecipeSource source, Collection<WorkerResourceKey> outputs){
        Set<WorkerResourceKey> visited = new HashSet<>(outputs);
        Set<List<WorkerResourceKey>> groups = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<WorkerResourceKey> pending = new ArrayDeque<>(outputs);
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        while(!pending.isEmpty()){
            DeferredWorkScheduler.checkpoint();
            for(WorkerRecipeDefinition def : source.producing(pending.removeFirst())){
                definitions.add(def);
                for(var ingredient : def.ingredients()){
                    if(!groups.add(ingredient.alternatives())) continue;
                    for(WorkerResourceKey resource : ingredient.alternatives())
                        if(visited.add(resource)) pending.addLast(resource);
                }
            }
        }
        return new WorkerRecipeRelationships(definitions);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private Scope scope(Collection<WorkerResourceKey> outputs){
        List<WorkerResourceKey> key = List.copyOf(outputs);
        synchronized(scopes){
            Scope ready = scopes.get(key);
            if(ready != null) return ready;
        }
        List<Node> nodes = new ArrayList<>();
        Map<Group, List<Use>> uses = new IdentityHashMap<>();
        Map<WorkerResourceKey, List<Group>> consumers = new HashMap<>();
        Set<WorkerResourceKey> visited = new HashSet<>(outputs);
        ArrayDeque<WorkerResourceKey> pending = new ArrayDeque<>(outputs);
        while(!pending.isEmpty()){
            DeferredWorkScheduler.checkpoint();
            for(Node node : producers.getOrDefault(pending.removeFirst(), List.of())){
                nodes.add(node);
                for(int idx = 0; idx < node.inputs.size(); idx++){
                    Group group = node.inputs.get(idx);
                    List<Use> users = uses.get(group);
                    if(users == null){
                        users = new ArrayList<>();
                        uses.put(group, users);
                        for(WorkerResourceKey resource : group.alternatives){
                            consumers.computeIfAbsent(resource, val -> new ArrayList<>()).add(group);
                            if(visited.add(resource)) pending.addLast(resource);
                        }
                    }
                    users.add(new Use(node, idx));
                }
            }
        }
        Scope res = new Scope(nodes, uses, consumers);
        synchronized(scopes){
            Scope ready = scopes.get(key);
            if(ready != null) return ready;
            if(nodes.size() <= MAX_SCOPED_RECIPES){
                while(!scopes.isEmpty() && (scopes.size() >= 64 || scopedRecipes + nodes.size() > MAX_SCOPED_RECIPES)){
                    var oldest = scopes.entrySet().iterator();
                    scopedRecipes -= oldest.next().getValue().nodes.size();
                    oldest.remove();
                }
                scopes.put(key, res);
                scopedRecipes += nodes.size();
            }
        }
        return res;
    }

    private static void propagate(Scope scope, PriorityQueue<Route> pending, Map<Group, Integer> costs,
                                   Set<Group> settled, Map<Node, Progress> progress,
                                   Map<WorkerResourceKey, Integer> production,
                                   Predicate<WorkerRecipeDefinition> supported,
                                   Map<WorkerResourceKey, WorkerRecipeDefinition> preferred){
        while(!pending.isEmpty()){
            DeferredWorkScheduler.checkpoint();
            Route route = pending.poll();
            if(route.cost != costs.getOrDefault(route.group, UNREACHABLE) || !settled.add(route.group)) continue;
            for(Use use : scope.uses.getOrDefault(route.group, List.of())){
                Node node = use.node;
                Progress state = progress.get(node);
                if(state.fulfilled[use.idx]) continue;
                state.fulfilled[use.idx] = true;
                state.cost = (int)Math.min(UNREACHABLE, (long)state.cost + route.cost);
                if(--state.remaining == 0 && supported.test(node.def))
                    offer(scope, node, state.cost, pending, costs, production, preferred);
            }
        }
    }

    // Probe preloaded inputs from the requested outputs, pruning branches with an unavailable mandatory input
    private void preload(Scope scope, Collection<WorkerResourceKey> outputs, PriorityQueue<Route> pending,
                           Map<Group, Integer> costs, Set<Group> settled, Map<Node, Progress> progress,
                           Map<WorkerResourceKey, Integer> production, Predicate<WorkerRecipeDefinition> supported,
                           WorkerRecipePlanner.IngredientStock machineStock,
                           Map<WorkerResourceKey, WorkerRecipeDefinition> preferred, boolean includePreloadedRoutes){
        Set<Node> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Group> expanded = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Group, Boolean> producible = new IdentityHashMap<>();
        List<Node> frontier = outputs.stream().flatMap(output -> producers.getOrDefault(output, List.of()).stream()).toList();
        while(!frontier.isEmpty()){
            List<Node> candidates = new ArrayList<>();
            List<Node> queried = new ArrayList<>();
            List<Supplier<List<Integer>>> queries = new ArrayList<>();
            for(Node node : frontier){
                DeferredWorkScheduler.checkpoint();
                if(!visited.add(node) || !supported.test(node.def)) continue;
                candidates.add(node);
                Progress state = progress.get(node);
                if(state.remaining == 0) continue;
                queried.add(node);
                queries.add(() -> {
                    List<Integer> fulfilled = new ArrayList<>();
                    for(int idx = 0; idx < node.inputs.size(); idx++){
                        if(!state.fulfilled[idx] && machineStock.available(node.def, idx) >= node.def.ingredients().get(idx).amount())
                            fulfilled.add(idx);
                    }
                    return fulfilled;
                });
            }
            var stockedInputs = DeferredWorkScheduler.queryTogether(queries);
            for(int idx = 0; idx < queried.size(); idx++){
                Node node = queried.get(idx);
                Progress state = progress.get(node);
                for(int slot : stockedInputs.get(idx)){
                    state.fulfilled[slot] = true;
                    state.remaining--;
                }
            }
            for(Node node : candidates) if(progress.get(node).remaining == 0)
                offer(scope, node, progress.get(node).cost, pending, costs, production, preferred);
            propagate(scope, pending, costs, settled, progress, production, supported, preferred);
            if(!includePreloadedRoutes && outputs.stream().allMatch(production::containsKey)) return;
            List<Node> next = new ArrayList<>();
            for(Node node : candidates){
                Progress state = progress.get(node);
                boolean blocked = false;
                for(int idx = 0; idx < node.inputs.size(); idx++){
                    if(state.fulfilled[idx]) continue;
                    if(!producible.computeIfAbsent(node.inputs.get(idx), group -> group.alternatives.stream()
                            .flatMap(resource -> producers.getOrDefault(resource, List.of()).stream())
                            .anyMatch(producer -> supported.test(producer.def)))){
                        blocked = true;
                        break;
                    }
                }
                if(blocked) continue;
                for(int idx = 0; idx < node.inputs.size(); idx++){
                    if(state.fulfilled[idx] && !includePreloadedRoutes) continue;
                    if(!expanded.add(node.inputs.get(idx))) continue;
                    for(var resource : node.inputs.get(idx).alternatives)
                        for(Node producer : producers.getOrDefault(resource, List.of()))
                            if(!visited.contains(producer)) next.add(producer);
                }
            }
            frontier = next;
        }
    }

    private static void offer(Scope scope, Node node, int cost, PriorityQueue<Route> pending,
                               Map<Group, Integer> costs, Map<WorkerResourceKey, Integer> production,
                               Map<WorkerResourceKey, WorkerRecipeDefinition> preferred){
        if(node.inputs.isEmpty() || cost >= UNREACHABLE) return;
        if(preferred != null){
            Integer prev = production.get(node.def.result());
            WorkerRecipeDefinition selected = preferred.get(node.def.result());
            if(prev == null || cost < prev || cost == prev && prefer(node.def, selected))
                preferred.put(node.def.result(), node.def);
        }
        production.merge(node.def.result(), cost, Math::min);
        for(Group group : scope.consumers.getOrDefault(node.def.result(), List.of())){
            if(cost >= costs.getOrDefault(group, UNREACHABLE)) continue;
            costs.put(group, cost);
            pending.add(new Route(group, cost));
        }
    }

    public record Ranks(Map<WorkerResourceKey, Integer> productionCosts, int recipeCount){}
    public record Routes(Ranks ranks, Map<WorkerResourceKey, WorkerRecipeDefinition> preferredRecipes){}

    private static boolean prefer(WorkerRecipeDefinition first, WorkerRecipeDefinition second){
        if(second == null) return true;
        if(first.operation() != second.operation()){
            if(first.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING) return true;
            if(second.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING) return false;
        }
        return first.recipeId().compareTo(second.recipeId()) < 0;
    }
    private record Node(WorkerRecipeDefinition def, List<Group> inputs){}
    private record Group(List<WorkerResourceKey> alternatives){}
    private record Route(Group group, int cost){}
    private record Use(Node node, int idx){}
    private record Scope(List<Node> nodes, Map<Group, List<Use>> uses,
                         Map<WorkerResourceKey, List<Group>> consumers){}
    private static final class Progress{
        private int remaining;
        private int cost = 1;
        private final boolean[] fulfilled;
        private Progress(int remaining){
            this.remaining = remaining;
            fulfilled = new boolean[remaining];
        }
    }
}
