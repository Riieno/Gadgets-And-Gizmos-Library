package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
import java.util.function.ToIntFunction;

// Search indexed prerequisites with a resumable work queue and one coordinated stock reservation
public final class WorkerRecipeSearch{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int UNKNOWN = 1_000_000;
    private static final int MAX_REMEMBERED_STATES = 16_384;
    private final WorkerRecipeSource source;
    private final Predicate<WorkerRecipeDefinition> supported;
    private final Predicate<WorkerRecipePlan> executable;
    private final Predicate<WorkerRecipePlan> rootExecutable;
    private final WorkerRecipePlanner.IngredientStock machineStock;
    private final boolean hasMachineStock;
    private final ToIntFunction<WorkerRecipePlan> routingPenalty;
    private final Map<WorkerRecipeDefinition, Integer> penalties = new IdentityHashMap<>();
    private final Map<WorkerResourceKey, Double> unitCosts = new HashMap<>();
    private Set<WorkerResourceKey> stocked = Set.of();
    private final Map<WorkerRecipeDefinition, Boolean> support = new IdentityHashMap<>();
    private final Map<WorkerRecipeDefinition, List<Long>> credits = new IdentityHashMap<>();
    private final Map<WorkerResourceKey, List<WorkerRecipeDefinition>> producers = new HashMap<>();
    private final PriorityQueue<State> pending = new PriorityQueue<>(Comparator.comparingLong(State::score)
            .thenComparing(Comparator.comparingLong(State::sequence).reversed()));
    private final ArrayDeque<State> preferredWork = new ArrayDeque<>();
    private final Set<WorkerRecipeDefinition> excluded = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Key, Boolean> seen = new LinkedHashMap<>();
    private final List<String> details = new ArrayList<>();
    private Map<WorkerResourceKey, Integer> costs = Map.of();
    private Map<WorkerResourceKey, WorkerRecipeDefinition> preferred = Map.of();
    private WorkerRecipeDefinition blocked;
    private boolean guided;
    private WorkerRecipePlan.Operation operation;
    private boolean includeMachineStock;
    private long sequence;

    private WorkerRecipeSearch(WorkerRecipeSource source, Predicate<WorkerRecipeDefinition> supported,
                                Predicate<WorkerRecipePlan> executable, Predicate<WorkerRecipePlan> rootExecutable,
                                ToIntFunction<WorkerRecipePlan> routingPenalty,
                                WorkerRecipePlanner.IngredientStock machineStock){
        this.source = source;
        this.supported = supported == null ? def -> true : supported;
        this.executable = executable == null ? plan -> true : executable;
        this.rootExecutable = rootExecutable == null ? plan -> true : rootExecutable;
        this.machineStock = machineStock == null ? (def, idx) -> 0L : machineStock;
        hasMachineStock = machineStock != null;
        this.routingPenalty = routingPenalty == null ? plan -> 0 : routingPenalty;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Plan complete prerequisites without a recursive tree depth or branch-count ceiling
    public static WorkerRecipePlanner.Result plan(WorkerResourceKey output, long amount,
                                                   Map<WorkerResourceKey, Long> available, WorkerRecipeSource source,
                                                   WorkerRecipePlan.Operation operation,
                                                   Predicate<WorkerRecipeDefinition> supported,
                                                   Predicate<WorkerRecipePlan> executable,
                                                   Predicate<WorkerRecipePlan> rootExecutable,
                                                   WorkerRecipePlanner.IngredientStock machineStock){
        return plan(output, amount, available, source, operation, supported, executable, rootExecutable, null, machineStock);
    }

    // Include live transport costs while retaining quantity-aware detached recipe ranking
    public static WorkerRecipePlanner.Result plan(WorkerResourceKey output, long amount,
                                                   Map<WorkerResourceKey, Long> available, WorkerRecipeSource source,
                                                   WorkerRecipePlan.Operation operation,
                                                   Predicate<WorkerRecipeDefinition> supported,
                                                   Predicate<WorkerRecipePlan> executable,
                                                   Predicate<WorkerRecipePlan> rootExecutable,
                                                   ToIntFunction<WorkerRecipePlan> routingPenalty,
                                                   WorkerRecipePlanner.IngredientStock machineStock){
        if(output == null || amount <= 0L) return new WorkerRecipePlanner.Result(new WorkerRecipeChain(List.of()),
                new WorkerFailureReason("invalid_request", "A recipe request needs a resource and positive amount"));
        WorkerRecipeSearch search = new WorkerRecipeSearch(source, supported, executable, rootExecutable, routingPenalty, machineStock);
        search.operation = operation;
        search.stocked = available.entrySet().stream().filter(entry -> entry.getValue() > 0L)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        List<WorkerRecipeDefinition> roots = search.producing(output);
        if(roots.isEmpty() && source instanceof WorkerRecipeRoutes routes){
            var unavailable = routes.unavailable(output);
            if(!unavailable.isEmpty()) return new WorkerRecipePlanner.Result(new WorkerRecipeChain(List.of()),
                    new WorkerFailureReason("missing_machine", "No linked machine can produce " + output.id()),
                    unavailable.stream().map(def -> "Machine route: " + def.processorType() + " for " + def.recipeId())
                            .distinct().limit(12).toList());
        }
        WorkerRecipePlan best = null;
        long bestCost = Long.MAX_VALUE;
        for(WorkerRecipeDefinition def : search.ordered(roots, amount, available)){
            long estimate = search.estimate(def, batches(amount, def.resultAmount()), available);
            if(best != null && estimate >= bestCost) break;
            if(!search.matches(def) || def.ingredients().isEmpty()) continue;
            long batches = batches(amount, def.resultAmount());
            if(batches > Long.MAX_VALUE / def.resultAmount()) continue;
            var direct = allocate(def, batches, available);
            if(direct == null || !search.supported(def)) continue;
            WorkerRecipePlan plan = selected(def, direct);
            if(!search.executable.test(plan) || !search.rootExecutable.test(plan)) continue;
            long cost = add(estimate, (long)Math.max(0, search.routingPenalty.applyAsInt(plan)) * 1024L);
            if(cost < bestCost){ best = plan; bestCost = cost; }
        }
        if(best != null) return new WorkerRecipePlanner.Result(new WorkerRecipeChain(List.of(new WorkerRecipeChain.Step(best, amount))),
                WorkerFailureReason.none());
        if(roots.stream().noneMatch(def -> search.matches(def) && !def.ingredients().isEmpty()
                && !Boolean.FALSE.equals(search.support.get(def)))) return search.failure();
        var graph = source.relationships(List.of(output));
        search.routes(graph, List.of(output), available, false);
        search.indexPrerequisites(graph, roots, available);
        State result = search.resolve(graph, List.of(output), available, new Work(new Make(output, amount, Set.of(), true), null));
        if(result == null) return search.failure();
        List<WorkerRecipeChain.Step> steps = steps(result.steps);
        var last = steps.getLast();
        steps.set(steps.size() - 1, new WorkerRecipeChain.Step(last.plan(), amount));
        return new WorkerRecipePlanner.Result(new WorkerRecipeChain(steps).grouped(), WorkerFailureReason.none());
    }

    // Repair only the unconsumed inputs of a persisted recipe using the same coordinated search
    public static WorkerRecipeChain prerequisites(WorkerRecipePlan plan, int inputIdx, long delivered, long batches,
                                                     Map<WorkerResourceKey, Long> available, WorkerRecipeSource source,
                                                     Predicate<WorkerRecipeDefinition> supported,
                                                     Predicate<WorkerRecipePlan> executable){
        if(plan == null || inputIdx < 0 || inputIdx >= plan.inputs().size()) return new WorkerRecipeChain(List.of());
        List<WorkerRecipeDefinition.Ingredient> inputs = new ArrayList<>();
        for(int idx = inputIdx; idx < plan.inputs().size(); idx++){
            var input = plan.inputs().get(idx);
            if(input.amount() > Long.MAX_VALUE / Math.max(1L, batches)) return new WorkerRecipeChain(List.of());
            long amount = input.amount() * Math.max(1L, batches) - (idx == inputIdx ? Math.max(0L, delivered) : 0L);
            if(amount <= 0L) continue;
            List<WorkerResourceKey> alternatives = input.alternatives();
            if(alternatives.size() == 1){
                var matches = source.producing(plan.result()).stream().filter(def -> def.recipeId().equals(plan.recipeId())
                                && def.processorType().equals(plan.processorType())).flatMap(def -> def.ingredients().stream())
                        .filter(ingredient -> ingredient.alternatives().contains(input.resource()))
                        .map(WorkerRecipeDefinition.Ingredient::alternatives).distinct().toList();
                if(matches.size() == 1) alternatives = matches.getFirst();
            }
            inputs.add(new WorkerRecipeDefinition.Ingredient(alternatives, amount));
        }
        if(inputs.isEmpty()) return new WorkerRecipeChain(List.of());
        WorkerRecipeSearch search = new WorkerRecipeSearch(source, supported, executable, candidate -> true, null, null);
        search.stocked = available.entrySet().stream().filter(entry -> entry.getValue() > 0L)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        var outputs = inputs.stream().flatMap(input -> input.alternatives().stream()).distinct().toList();
        var graph = source.relationships(outputs);
        search.routes(graph, outputs, available, false);
        var def = new WorkerRecipeDefinition(plan.recipeId(), plan.processorType(), plan.operation(), inputs,
                plan.result(), plan.resultAmount());
        State result = search.resolve(graph, outputs, available,
                new Work(search.build(def, 1L, Set.of(plan.result()), false, available, true), null));
        if(result == null) return new WorkerRecipeChain(List.of());
        var steps = steps(result.steps);
        steps.removeLast();
        return new WorkerRecipeChain(steps).grouped();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Search stocked routes first, then probe preloaded machines only if those routes cannot finish
    private State resolve(WorkerRecipeRelationships graph, List<WorkerResourceKey> outputs,
                            Map<WorkerResourceKey, Long> available, Work work){
        guided = true;
        for(int attempt = 0; attempt < 64; attempt++){
            blocked = null;
            offer(available, null, work);
            State result = run();
            boolean changed = false;
            if(result != null){
                changed = validate(result.steps);
                if(blocked == null && !changed) return result;
            }
            preferredWork.clear();
            seen.clear();
            if(blocked == null && changed){
                priceRoutes();
                continue;
            }
            if(blocked == null || !excluded.add(blocked)) break;
            routes(graph, outputs, available, false);
        }
        guided = false;
        excluded.clear();
        routes(graph, outputs, available, false);
        offer(available, null, work);
        State result = run();
        if(result != null || !hasMachineStock) return result;
        includeMachineStock = true;
        costs = graph.rank(outputs, available, def -> !Boolean.FALSE.equals(support.get(def)), this::credit, true).productionCosts();
        seen.clear();
        offer(available, null, work);
        return run();
    }

    // Validate a completed dependency graph together instead of waiting on each prerequisite in turn
    private boolean validate(Completed steps){
        List<Supplier<Boolean>> checks = new ArrayList<>();
        for(var cursor = steps; cursor != null; cursor = cursor.prev){
            var step = cursor;
            if(step.repair) continue;
            checks.add(() -> {
                var def = step.def;
                if(!support.computeIfAbsent(def, supported::test)){
                    detail("Machine route: " + def.processorType() + " for " + def.recipeId());
                    blocked = def;
                    return false;
                }
                List<Long> prev = credits.get(def);
                List<Long> stocked = new ArrayList<>();
                for(int idx = 0; idx < def.ingredients().size(); idx++)
                    stocked.add(Math.max(0L, machineStock.available(def, idx)));
                credits.put(def, List.copyOf(stocked));
                boolean changed = prev == null ? stocked.stream().anyMatch(amount -> amount > 0L) : !prev.equals(stocked);
                int penalty = Math.max(0, routingPenalty.applyAsInt(step.step.plan()));
                Integer prevPenalty = penalties.put(def, penalty);
                changed |= prevPenalty == null ? penalty > 0 : prevPenalty != penalty;
                if(!executable.test(step.step.plan()) || step.root && !rootExecutable.test(step.step.plan())){
                    detail("Machine route: " + def.processorType() + " for " + def.recipeId());
                    blocked = def;
                }
                return changed;
            });
        }
        return DeferredWorkScheduler.queryTogether(checks).stream().anyMatch(Boolean::booleanValue);
    }

    private void routes(WorkerRecipeRelationships graph, List<WorkerResourceKey> outputs,
                         Map<WorkerResourceKey, Long> available, boolean machines){
        var routes = graph.routes(outputs, available,
                def -> !excluded.contains(def) && !Boolean.FALSE.equals(support.get(def)), machines ? this::credit : null, machines);
        costs = routes.ranks().productionCosts();
        preferred = routes.preferredRecipes();
        priceRoutes();
    }

    // Carry ingredient quantities and recipe yields through the acyclic producing witnesses
    private void priceRoutes(){
        unitCosts.clear();
        for(var resource : stocked) unitCosts.put(resource, 1D);
        for(var entry : preferred.entrySet().stream().sorted(Comparator.comparingInt(entry ->
                costs.getOrDefault(entry.getKey(), UNKNOWN))).toList()){
            DeferredWorkScheduler.checkpoint();
            var def = entry.getValue();
            double cost = 1D + penalties.getOrDefault(def, 0);
            for(var input : def.ingredients()){
                double unit = input.alternatives().stream().mapToDouble(resource -> unitCosts.getOrDefault(resource, (double)UNKNOWN))
                        .min().orElse(UNKNOWN);
                cost += input.amount() * unit;
            }
            unitCosts.merge(entry.getKey(), cost / def.resultAmount(), Math::min);
        }
    }

    // Read independent producer buckets on the bounded background scheduler before reserving their shared stock
    private void indexPrerequisites(WorkerRecipeRelationships graph, List<WorkerRecipeDefinition> roots,
                                     Map<WorkerResourceKey, Long> available){
        var resources = roots.stream().flatMap(def -> def.ingredients().stream())
                .flatMap(input -> input.alternatives().stream()).distinct()
                .filter(key -> available.getOrDefault(key, 0L) <= 0L && costs.containsKey(key)).toList();
        List<Supplier<Map<WorkerResourceKey, List<WorkerRecipeDefinition>>>> tasks = new ArrayList<>();
        for(int part = 0; part < Math.min(2, resources.size()); part++){
            int offset = part;
            tasks.add(() -> {
                Map<WorkerResourceKey, List<WorkerRecipeDefinition>> found = new HashMap<>();
                for(int idx = offset; idx < resources.size(); idx += 2){
                    DeferredWorkScheduler.checkpoint();
                    var resource = resources.get(idx);
                    found.put(resource, graph.producing(resource));
                }
                return found;
            });
        }
        for(var found : DeferredWorkScheduler.parallel(tasks)) producers.putAll(found);
    }

    private State run(){
        while(guided ? !preferredWork.isEmpty() : !pending.isEmpty()){
            DeferredWorkScheduler.checkpoint();
            State state = guided ? preferredWork.removeLast() : pending.poll();
            if(state.work == null) return state;
            Key key = new Key(state.stock, state.work);
            if(seen.putIfAbsent(key, Boolean.TRUE) != null) continue;
            if(seen.size() > MAX_REMEMBERED_STATES) seen.remove(seen.keySet().iterator().next());
            Task task = state.work.task;
            Work next = state.work.next;
            if(task instanceof Make make){
                if(make.path.contains(make.resource)) continue;
                List<WorkerRecipeDefinition> choices;
                if(guided){
                    choices = ordered(producing(make.resource), make.amount, state.stock).stream()
                            .filter(def -> !excluded.contains(def) && !Boolean.FALSE.equals(support.get(def))
                                    && (!make.root || matches(def))).limit(1).toList();
                }else choices = ordered(producing(make.resource), make.amount, state.stock);
                if(choices.isEmpty()) detail("Ingredient: " + make.resource.id() + " has no producing recipe");
                offer(state.stock, state.steps, new Work(new Choice(make, choices, 0), next));
            }else if(task instanceof Choice choice){
                if(choice.idx >= choice.definitions.size()) continue;
                var def = choice.definitions.get(choice.idx);
                offer(state.stock, state.steps, new Work(new Choice(choice.make, choice.definitions, choice.idx + 1), next));
                if(choice.make.root && !matches(def) || !supported(def)){
                    if(guided) blocked = def;
                    continue;
                }
                long batches = batches(choice.make.amount, def.resultAmount());
                if(batches > Long.MAX_VALUE / def.resultAmount()) continue;
                if(!reachable(def, batches, state.stock)){
                    if(guided) blocked = def;
                    continue;
                }
                Set<WorkerResourceKey> path = new HashSet<>(choice.make.path);
                path.add(choice.make.resource);
                offer(state.stock, state.steps, new Work(build(def, batches, Set.copyOf(path), choice.make.root,
                        state.stock, false), next));
            }else if(task instanceof Build build){
                expand(state, build, next);
            }else if(task instanceof Supply supply){
                if(supply.idx >= supply.alternatives.size()) continue;
                var resource = supply.alternatives.get(supply.idx);
                offer(state.stock, state.steps, new Work(new Supply(supply.build, supply.slot, supply.needed,
                        supply.shortage, supply.alternatives, supply.idx + 1), next));
                Work resume = new Work(new Resume(supply.build, supply.slot, supply.needed - supply.shortage), next);
                offer(state.stock, state.steps, new Work(new Make(resource, supply.shortage, supply.build.path, false), resume));
                if(supply.alternatives.size() > 1){
                    long partial = partialAmount(resource, supply.shortage, state.stock);
                    if(partial > 0L && partial < supply.shortage)
                        offer(state.stock, state.steps, new Work(new Make(resource, partial, supply.build.path, false), resume));
                }
            }else if(task instanceof Resume resume){
                long stocked = 0L;
                for(var resource : resume.build.def.ingredients().get(resume.slot).alternatives())
                    stocked = add(stocked, state.stock.getOrDefault(resource, 0L));
                if(stocked > resume.stocked)
                    offer(state.stock, state.steps, new Work(resume.build, next));
            }
        }
        return null;
    }

    private void expand(State state, Build build, Work next){
        if(build.idx == build.order.size()){
            WorkerRecipePlan plan = new WorkerRecipePlan(build.def.recipeId(), build.def.processorType(), build.def.operation(),
                    build.selected, build.def.result(), build.def.resultAmount());
            if(!guided && !build.repair && (!executable.test(plan) || build.root && !rootExecutable.test(plan))){
                detail("Machine route: " + plan.processorType() + " for " + plan.recipeId());
                if(guided) blocked = build.def;
                return;
            }
            Map<WorkerResourceKey, Long> stock = new HashMap<>(state.stock);
            stock.merge(build.def.result(), build.batches * build.def.resultAmount(), WorkerRecipeSearch::add);
            var step = new Completed(build.def, new WorkerRecipeChain.Step(plan, build.batches * build.def.resultAmount()),
                    build.root, build.repair, state.steps);
            offer(stock, step, next);
            return;
        }
        if(stockedRemainder(state, build, next) && guided) return;
        int slot = build.order.get(build.idx);
        var input = build.def.ingredients().get(slot);
        if(build.batches > Long.MAX_VALUE / input.amount()) return;
        long needed = Math.max(0L, build.batches * input.amount() - credit(build.def, slot));
        if(needed == 0L){
            if(!input.alternatives().isEmpty()) reserve(state, build, slot, 0L, input.alternatives().getFirst(), next);
            return;
        }
        long stocked = 0L;
        for(var resource : input.alternatives()) stocked = add(stocked, state.stock.getOrDefault(resource, 0L));
        if(stocked >= needed){
            reserve(state, build, slot, needed, null, next);
            for(var resource : input.alternatives()){
                if(state.stock.getOrDefault(resource, 0L) >= needed)
                    reserve(state, build, slot, needed, resource, next);
            }
            return;
        }
        var alternatives = input.alternatives().stream().filter(resource -> !build.path.contains(resource))
                .filter(costs::containsKey)
                .sorted(Comparator.comparingDouble(resource -> unitCosts.getOrDefault(resource, (double)UNKNOWN))).toList();
        if(alternatives.isEmpty()){
            detail("Ingredient: " + input.alternatives() + " needs " + needed + ", linked stock " + stocked);
            if(guided) blocked = build.def;
            return;
        }
        offer(state.stock, state.steps, new Work(new Supply(build, slot, needed, needed - stocked, alternatives, 0), next));
    }

    // Allocate remaining tag slots together once newly crafted inputs make them fully stocked
    private boolean stockedRemainder(State state, Build build, Work next){
        List<WorkerRecipeDefinition.Ingredient> inputs = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        for(int idx = build.idx; idx < build.order.size(); idx++){
            int slot = build.order.get(idx);
            var input = build.def.ingredients().get(slot);
            if(build.batches > Long.MAX_VALUE / input.amount()) return false;
            inputs.add(input);
            amounts.add(Math.max(0L, build.batches * input.amount() - credit(build.def, slot)));
        }
        var allocation = WorkerIngredientAllocation.allocate(inputs, amounts, state.stock);
        if(allocation == null) return false;
        Map<WorkerResourceKey, Long> stock = new HashMap<>(state.stock);
        List<WorkerRecipePlan.Input> selected = new ArrayList<>(build.selected);
        for(int idx = 0; idx < inputs.size(); idx++){
            var input = inputs.get(idx);
            var resource = allocation.get(idx).entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElseGet(() -> input.alternatives().getFirst());
            selected.set(build.order.get(build.idx + idx), new WorkerRecipePlan.Input(resource, input.amount(), input.alternatives()));
            allocation.get(idx).forEach((key, amount) -> stock.compute(key, (val, count) -> count - amount));
        }
        stock.values().removeIf(amount -> amount <= 0L);
        var completed = new Build(build.def, build.batches, build.order, build.order.size(),
                List.copyOf(selected), build.path, build.root, build.repair);
        offer(stock, state.steps, new Work(completed, next));
        return true;
    }

    private void reserve(State state, Build build, int slot, long needed, WorkerResourceKey representative, Work next){
        var input = build.def.ingredients().get(slot);
        Map<WorkerResourceKey, Long> stock = new HashMap<>(state.stock);
        long remaining = needed;
        if(representative != null && stock.getOrDefault(representative, 0L) >= needed){
            long left = stock.getOrDefault(representative, 0L) - needed;
            if(left == 0L) stock.remove(representative);
            else stock.put(representative, left);
            remaining = 0L;
        }else{
            for(var resource : input.alternatives()){
                long take = Math.min(remaining, stock.getOrDefault(resource, 0L));
                if(take <= 0L) continue;
                if(representative == null) representative = resource;
                long left = stock.get(resource) - take;
                if(left == 0L) stock.remove(resource);
                else stock.put(resource, left);
                remaining -= take;
                if(remaining == 0L) break;
            }
        }
        if(remaining > 0L || representative == null) return;
        List<WorkerRecipePlan.Input> selected = new ArrayList<>(build.selected);
        selected.set(slot, new WorkerRecipePlan.Input(representative, input.amount(), input.alternatives()));
        var advanced = new Build(build.def, build.batches, build.order, build.idx + 1,
                java.util.Collections.unmodifiableList(selected), build.path, build.root, build.repair);
        offer(stock, state.steps, new Work(advanced, next));
    }

    private Build build(WorkerRecipeDefinition def, long batches, Set<WorkerResourceKey> path, boolean root,
                          Map<WorkerResourceKey, Long> stock, boolean repair){
        List<Integer> order = new ArrayList<>();
        for(int idx = 0; idx < def.ingredients().size(); idx++) order.add(idx);
        order.sort(Comparator.comparingInt((Integer idx) -> def.ingredients().get(idx).alternatives().size())
                .thenComparingInt(idx -> def.ingredients().get(idx).alternatives().stream()
                        .anyMatch(resource -> stock.getOrDefault(resource, 0L) > 0L) ? 0 : 1));
        var selected = new ArrayList<WorkerRecipePlan.Input>(java.util.Collections.nCopies(order.size(), null));
        return new Build(def, batches, List.copyOf(order), 0, java.util.Collections.unmodifiableList(selected), path, root, repair);
    }

    private static List<Map<WorkerResourceKey, Long>> allocate(WorkerRecipeDefinition def, long batches,
                                                              Map<WorkerResourceKey, Long> stock){
        List<Long> amounts = new ArrayList<>();
        for(int idx = 0; idx < def.ingredients().size(); idx++){
            long amount = def.ingredients().get(idx).amount();
            if(batches > Long.MAX_VALUE / amount) return null;
            amounts.add(batches * amount);
        }
        return WorkerIngredientAllocation.allocate(def.ingredients(), amounts, stock);
    }

    private static WorkerRecipePlan selected(WorkerRecipeDefinition def, List<Map<WorkerResourceKey, Long>> allocation){
        List<WorkerRecipePlan.Input> inputs = new ArrayList<>();
        for(int idx = 0; idx < def.ingredients().size(); idx++){
            var ingredient = def.ingredients().get(idx);
            var resource = allocation.get(idx).entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElseGet(() -> ingredient.alternatives().getFirst());
            inputs.add(new WorkerRecipePlan.Input(resource, ingredient.amount(), ingredient.alternatives()));
        }
        return new WorkerRecipePlan(def.recipeId(), def.processorType(), def.operation(), inputs, def.result(), def.resultAmount());
    }

    private List<WorkerRecipeDefinition> producing(WorkerResourceKey resource){
        return producers.computeIfAbsent(resource, key -> {
            var found = source.producing(key);
            if(found.isEmpty() && source instanceof WorkerRecipeRoutes routes)
                routes.unavailable(key).stream().limit(3).forEach(def ->
                        detail("Machine route: " + def.processorType() + " for " + def.recipeId()));
            return found;
        });
    }

    // Use a stocked portion of one tag member before producing the rest from another member
    private long partialAmount(WorkerResourceKey resource, long required, Map<WorkerResourceKey, Long> stock){
        long best = 0L;
        for(var def : producing(resource)){
            long batches = Long.MAX_VALUE;
            for(var ingredient : def.ingredients()){
                long available = 0L;
                for(var alternative : ingredient.alternatives()) available = add(available, stock.getOrDefault(alternative, 0L));
                batches = Math.min(batches, available / ingredient.amount());
            }
            if(batches <= 0L || batches == Long.MAX_VALUE) continue;
            long output = batches > Long.MAX_VALUE / def.resultAmount() ? Long.MAX_VALUE : batches * def.resultAmount();
            best = Math.max(best, Math.min(required, output));
        }
        if(best == 0L) best = producing(resource).stream().mapToLong(WorkerRecipeDefinition::resultAmount).min().orElse(0L);
        return Math.min(required, best);
    }

    private boolean supported(WorkerRecipeDefinition def){
        return !def.ingredients().isEmpty() && def.ingredients().stream().noneMatch(input -> input.alternatives().isEmpty())
                && (guided ? !Boolean.FALSE.equals(support.get(def)) : support.computeIfAbsent(def, supported::test));
    }

    private boolean reachable(WorkerRecipeDefinition def, long batches, Map<WorkerResourceKey, Long> stock){
        for(int idx = 0; idx < def.ingredients().size(); idx++){
            var input = def.ingredients().get(idx);
            if(batches > Long.MAX_VALUE / input.amount()) return false;
            long amount = 0L;
            for(var resource : input.alternatives()) amount = add(amount, stock.getOrDefault(resource, 0L));
            long required = Math.max(0L, batches * input.amount() - (includeMachineStock ? credit(def, idx) : 0L));
            if(amount < required && input.alternatives().stream().noneMatch(costs::containsKey)){
                detail("Ingredient: " + input.alternatives() + " needs " + required + ", linked stock " + amount);
                if(source instanceof WorkerRecipeRoutes routes) input.alternatives().stream().limit(3)
                        .flatMap(resource -> routes.unavailable(resource).stream()).limit(3)
                        .forEach(recipe -> detail("Machine route: " + recipe.processorType() + " for " + recipe.recipeId()));
                return false;
            }
        }
        return true;
    }

    private long credit(WorkerRecipeDefinition def, int idx){
        if(guided){
            var stocked = credits.get(def);
            return stocked == null ? 0L : stocked.get(idx);
        }
        return credits.computeIfAbsent(def, key -> {
            List<Long> values = new ArrayList<>();
            for(int slot = 0; slot < def.ingredients().size(); slot++) values.add(Math.max(0L, machineStock.available(def, slot)));
            return List.copyOf(values);
        }).get(idx);
    }

    private boolean matches(WorkerRecipeDefinition def){
        return operation == null || def.operation() == operation || operation == WorkerRecipePlan.Operation.CRAFTING
                && def.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING;
    }

    private List<WorkerRecipeDefinition> ordered(List<WorkerRecipeDefinition> definitions, long amount,
                                                 Map<WorkerResourceKey, Long> stock){
        return definitions.stream().filter(def -> !def.ingredients().isEmpty())
                .sorted(Comparator.comparingLong((WorkerRecipeDefinition def) -> estimate(def, batches(amount, def.resultAmount()), stock))
                        .thenComparingInt(def -> def.operation() == WorkerRecipePlan.Operation.PROCESSING ? 0 : 1)
                        .thenComparing(WorkerRecipeDefinition::recipeId)).toList();
    }

    private long estimate(WorkerRecipeDefinition def, long batches, Map<WorkerResourceKey, Long> stock){
        double cost = batches + penalties.getOrDefault(def, 0);
        for(int idx = 0; idx < def.ingredients().size(); idx++){
            var ingredient = def.ingredients().get(idx);
            double best = UNKNOWN;
            for(var resource : ingredient.alternatives()){
                best = Math.min(best, unitCosts.getOrDefault(resource,
                        stocked.contains(resource) || stock.getOrDefault(resource, 0L) > 0L ? 1D : (double)UNKNOWN));
            }
            long required = batches > Long.MAX_VALUE / ingredient.amount() ? Long.MAX_VALUE : batches * ingredient.amount();
            var credited = credits.get(def);
            cost += Math.max(0L, required - (credited == null ? 0L : credited.get(idx))) * best;
        }
        return (long)Math.min(Long.MAX_VALUE, Math.ceil(cost * 1024D));
    }

    private void offer(Map<WorkerResourceKey, Long> stock, Completed steps, Work work){
        if(guided){
            preferredWork.addLast(new State(Map.copyOf(stock), steps, work, 0L, sequence++));
            return;
        }
        long remaining = 0L;
        if(work != null){
            if(work.task instanceof Make make) remaining = costs.getOrDefault(make.resource, UNKNOWN);
            else if(work.task instanceof Choice choice && choice.idx < choice.definitions.size())
                remaining = estimate(choice.definitions.get(choice.idx), batches(choice.make.amount,
                        choice.definitions.get(choice.idx).resultAmount()), stock);
            else if(work.task instanceof Build build){
                remaining = 1L;
                for(int idx = build.idx; idx < build.order.size(); idx++){
                    var ingredient = build.def.ingredients().get(build.order.get(idx));
                    if(ingredient.alternatives().stream().noneMatch(resource -> stock.getOrDefault(resource, 0L) > 0L))
                        remaining = add(remaining, ingredient.alternatives().stream()
                                .mapToInt(resource -> costs.getOrDefault(resource, UNKNOWN)).min().orElse(UNKNOWN));
                }
            }else if(work.task instanceof Supply supply && supply.idx < supply.alternatives.size())
                remaining = costs.getOrDefault(supply.alternatives.get(supply.idx), UNKNOWN) + 1L;
        }
        var state = new State(Map.copyOf(stock), steps, work, remaining, sequence++);
        pending.add(state);
    }

    private void detail(String value){ if(details.size() < 12 && !details.contains(value)) details.add(value); }
    private WorkerRecipePlanner.Result failure(){
        return new WorkerRecipePlanner.Result(new WorkerRecipeChain(List.of()),
                new WorkerFailureReason("recipe_input_unavailable", "No complete recipe schedule can use the available ingredients and machines"),
                List.copyOf(details));
    }
    private static long batches(long amount, long output){ return (amount - 1L) / output + 1L; }
    private static long add(long first, long second){ return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second; }
    private static List<WorkerRecipeChain.Step> steps(Completed completed){
        List<WorkerRecipeChain.Step> steps = new ArrayList<>();
        for(var cursor = completed; cursor != null; cursor = cursor.prev) steps.add(cursor.step);
        java.util.Collections.reverse(steps);
        return steps;
    }

    private interface Task{}
    private record Make(WorkerResourceKey resource, long amount, Set<WorkerResourceKey> path, boolean root) implements Task{}
    private record Choice(Make make, List<WorkerRecipeDefinition> definitions, int idx) implements Task{}
    private record Build(WorkerRecipeDefinition def, long batches, List<Integer> order, int idx,
                         List<WorkerRecipePlan.Input> selected, Set<WorkerResourceKey> path,
                         boolean root, boolean repair) implements Task{}
    private record Supply(Build build, int slot, long needed, long shortage,
                          List<WorkerResourceKey> alternatives, int idx) implements Task{}
    private record Resume(Build build, int slot, long stocked) implements Task{}
    private record State(Map<WorkerResourceKey, Long> stock, Completed steps, Work work, long score, long sequence){}
    private record Key(Map<WorkerResourceKey, Long> stock, Work work){}
    private static final class Completed{
        private final WorkerRecipeDefinition def;
        private final WorkerRecipeChain.Step step;
        private final boolean root;
        private final boolean repair;
        private final Completed prev;
        private Completed(WorkerRecipeDefinition def, WorkerRecipeChain.Step step, boolean root, boolean repair, Completed prev){
            this.def = def;
            this.step = step;
            this.root = root;
            this.repair = repair;
            this.prev = prev;
        }
    }
    private static final class Work{
        private final Task task;
        private final Work next;
        private final int hash;
        private Work(Task task, Work next){
            this.task = task;
            this.next = next;
            int value;
            if(task instanceof Choice choice) value = 31 * choice.make.hashCode() + choice.idx;
            else if(task instanceof Build build) value = 31 * build.def.recipeId().hashCode() + build.idx;
            else if(task instanceof Supply supply) value = 31 * supply.build.def.recipeId().hashCode() + supply.idx;
            else if(task instanceof Resume resume) value = 31 * resume.build.def.recipeId().hashCode() + resume.slot;
            else value = task.hashCode();
            hash = 31 * value + (next == null ? 0 : next.hash);
        }
        @Override public int hashCode(){ return hash; }
        @Override public boolean equals(Object other){
            if(!(other instanceof Work work) || hash != work.hash) return false;
            Work left = this, right = work;
            while(left != right){
                if(left == null || right == null || !left.task.equals(right.task)) return false;
                left = left.next;
                right = right.next;
            }
            return true;
        }
    }
}
