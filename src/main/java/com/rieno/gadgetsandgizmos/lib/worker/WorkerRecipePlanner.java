package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;

// Resolve complete recipe trees without consuming live inventories
public final class WorkerRecipePlanner{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_DEPTH = 64;
    private static final int MAX_BRANCHES = 32768;
    private static final int MAX_GRAPH_CHECKS = 65536;
    private static final int UNREACHABLE = Integer.MAX_VALUE / 4;
    private final WorkerRecipeIndex recipes;
    private final Map<WorkerResourceKey, Integer> routeCosts = new HashMap<>();
    private final Map<WorkerResourceKey, Integer> productionCosts = new HashMap<>();
    private final Map<WorkerRecipeDefinition, Boolean> supportedCache = new IdentityHashMap<>();
    private final Predicate<WorkerRecipeDefinition> supported;
    private final Predicate<WorkerRecipePlan> executable;
    private final Predicate<WorkerRecipePlan> rootExecutable;
    private final ToIntFunction<WorkerRecipePlan> routingPenalty;
    private final IngredientStock machineStock;
    private int branches;
    private int demandChecks;
    private int supplyChecks;
    private WorkerFailureReason failure = new WorkerFailureReason("recipe_unavailable", "No complete recipe schedule is available");
    private final Set<String> failureDetails = new LinkedHashSet<>();

    // Retain the shared graph while checking machine support for this worker's current links
    private WorkerRecipePlanner(WorkerRecipeIndex recipes, Predicate<WorkerRecipeDefinition> supported,
                                Predicate<WorkerRecipePlan> executable, Predicate<WorkerRecipePlan> rootExecutable,
                                ToIntFunction<WorkerRecipePlan> routingPenalty, IngredientStock machineStock){
        this.recipes = recipes;
        this.supported = supported;
        this.executable = executable;
        this.rootExecutable = rootExecutable;
        this.routingPenalty = routingPenalty;
        this.machineStock = machineStock;
    }

    private boolean supported(WorkerRecipeDefinition def){
        return supportedCache.computeIfAbsent(def, supported::test);
    }

    private void recordFailureDetail(String detail){
        if(failureDetails.size() < 32) failureDetails.add(detail);
    }

    private long machineCredit(WorkerRecipeDefinition def, int idx, long crafts){
        long amount = def.ingredients().get(idx).amount();
        long required = crafts > Long.MAX_VALUE / amount ? Long.MAX_VALUE : crafts * amount;
        return Math.min(required, Math.max(0L, machineStock.available(def, idx)));
    }

    // Report stock already held in one selected machine input, indexed by recipe ingredient
    @FunctionalInterface public interface IngredientStock{
        long available(WorkerRecipeDefinition recipe, int ingredientIndex);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Plan the requested output and reserve all shared inputs on a detached inventory snapshot
    public static WorkerRecipeChain plan(WorkerResourceKey result, long amount,
                                         Map<WorkerResourceKey, Long> available,
                                         List<WorkerRecipeDefinition> recipes,
                                         WorkerRecipePlan.Operation operation,
                                         Predicate<WorkerRecipePlan> executable){
        return planDetailed(result, amount, available, recipes, operation, executable).chain();
    }

    // Return a useful failure alongside an all-or-nothing schedule
    public static Result planDetailed(WorkerResourceKey result, long amount,
                                       Map<WorkerResourceKey, Long> available,
                                       List<WorkerRecipeDefinition> recipes,
                                       WorkerRecipePlan.Operation operation,
                                       Predicate<WorkerRecipePlan> executable){
        return planDetailed(result, amount, available, recipes, operation, executable, null);
    }

    // Constrain the final recipe to a selected machine while allowing other machines for prerequisites
    public static Result planDetailed(WorkerResourceKey result, long amount,
                                       Map<WorkerResourceKey, Long> available,
                                       List<WorkerRecipeDefinition> recipes,
                                       WorkerRecipePlan.Operation operation,
                                       Predicate<WorkerRecipePlan> executable,
                                       Predicate<WorkerRecipePlan> rootExecutable){
        return planDetailed(result, amount, available, new WorkerRecipeIndex(recipes), operation,
                def -> true, executable, rootExecutable);
    }

    // Plan from a cached recipe graph while checking only reachable machine routes
    public static Result planDetailed(WorkerResourceKey result, long amount,
                                      Map<WorkerResourceKey, Long> available,
                                      WorkerRecipeIndex recipes,
                                      WorkerRecipePlan.Operation operation,
                                      Predicate<WorkerRecipeDefinition> supported,
                                      Predicate<WorkerRecipePlan> executable,
                                      Predicate<WorkerRecipePlan> rootExecutable){
        if(result == null || amount <= 0L) return new Result(new WorkerRecipeChain(List.of()),
                new WorkerFailureReason("invalid_request", "A recipe request needs a resource and positive amount"));
        return planDetailed(result, amount, available, recipes, operation, supported, executable,
                rootExecutable, plan -> 0);
    }

    // Prefer routes that do not need to isolate a shared transport line
    public static Result planDetailed(WorkerResourceKey result, long amount,
                                      Map<WorkerResourceKey, Long> available,
                                      WorkerRecipeIndex recipes,
                                      WorkerRecipePlan.Operation operation,
                                      Predicate<WorkerRecipeDefinition> supported,
                                      Predicate<WorkerRecipePlan> executable,
                                      Predicate<WorkerRecipePlan> rootExecutable,
                                      ToIntFunction<WorkerRecipePlan> routingPenalty){
        return planDetailed(result, amount, available, recipes, operation, supported, executable,
                rootExecutable, routingPenalty, (recipe, idx) -> 0L);
    }

    // Credit ingredients already in a matching machine without treating them as withdrawable storage
    public static Result planDetailed(WorkerResourceKey result, long amount,
                                      Map<WorkerResourceKey, Long> available,
                                      WorkerRecipeIndex recipes,
                                      WorkerRecipePlan.Operation operation,
                                      Predicate<WorkerRecipeDefinition> supported,
                                      Predicate<WorkerRecipePlan> executable,
                                      Predicate<WorkerRecipePlan> rootExecutable,
                                      ToIntFunction<WorkerRecipePlan> routingPenalty,
                                      IngredientStock machineStock){
        if(result == null || amount <= 0L) return new Result(new WorkerRecipeChain(List.of()),
                new WorkerFailureReason("invalid_request", "A recipe request needs a resource and positive amount"));
        WorkerRecipePlanner planner = new WorkerRecipePlanner(recipes, supported, executable, rootExecutable,
                routingPenalty == null ? plan -> 0 : routingPenalty,
                machineStock == null ? (recipe, idx) -> 0L : machineStock);
        State state = new State(new LinkedHashMap<>(available), new ArrayList<>());
        planner.rankStockRoutes(state.available, List.of(result));
        State planned = planner.produce(result, amount, state, new HashSet<>(), operation, 0, Function.identity());
        if(planned == null || planned.steps.isEmpty()){
            if(planner.failureDetails.isEmpty()) planner.explainUnavailable(result, amount, state, new HashSet<>(), 0);
            return new Result(new WorkerRecipeChain(List.of()),
                    planner.failure, planner.failureDetails.stream().limit(12).toList());
        }
        WorkerRecipeChain.Step last = planned.steps.getLast();
        planned.steps.set(planned.steps.size() - 1, new WorkerRecipeChain.Step(last.plan(), amount));
        return new Result(new WorkerRecipeChain(planned.steps).grouped(), WorkerFailureReason.none());
    }

    // Resolve every still-missing input of an in-progress recipe without repeating consumed inputs
    public static WorkerRecipeChain prerequisites(WorkerRecipePlan plan, int inputIdx, long delivered,
                                                  Map<WorkerResourceKey, Long> available,
                                                  List<WorkerRecipeDefinition> recipes,
                                                  Predicate<WorkerRecipePlan> executable){
        return prerequisites(plan, inputIdx, delivered, 1L, available, recipes, executable);
    }

    // Repair every missing input for the selected production visit at once
    public static WorkerRecipeChain prerequisites(WorkerRecipePlan plan, int inputIdx, long delivered, long batches,
                                                  Map<WorkerResourceKey, Long> available,
                                                  List<WorkerRecipeDefinition> recipes,
                                                  Predicate<WorkerRecipePlan> executable){
        return prerequisites(plan, inputIdx, delivered, batches, available, new WorkerRecipeIndex(recipes),
                def -> true, executable);
    }

    // Repair a saved plan from the shared graph and current machine links
    public static WorkerRecipeChain prerequisites(WorkerRecipePlan plan, int inputIdx, long delivered, long batches,
                                                  Map<WorkerResourceKey, Long> available,
                                                  WorkerRecipeIndex recipes,
                                                  Predicate<WorkerRecipeDefinition> supported,
                                                  Predicate<WorkerRecipePlan> executable){
        if(plan == null || inputIdx < 0 || inputIdx >= plan.inputs().size()) return new WorkerRecipeChain(List.of());
        List<WorkerRecipeDefinition.Ingredient> ingredients = new ArrayList<>();
        for(int idx = inputIdx; idx < plan.inputs().size(); idx++){
            WorkerRecipePlan.Input input = plan.inputs().get(idx);
            if(input.amount() > Long.MAX_VALUE / Math.max(1L, batches)) return new WorkerRecipeChain(List.of());
            long needed = input.amount() * Math.max(1L, batches)
                    - (idx == inputIdx ? Math.max(0L, delivered) : 0L);
            if(needed > 0L) ingredients.add(new WorkerRecipeDefinition.Ingredient(
                    acceptedAlternatives(plan, input, recipes), needed));
        }
        if(ingredients.isEmpty()) return new WorkerRecipeChain(List.of());
        WorkerRecipeDefinition def = new WorkerRecipeDefinition(plan.recipeId(), plan.processorType(),
                plan.operation(), ingredients, plan.result(), plan.resultAmount());
        WorkerRecipePlanner planner = new WorkerRecipePlanner(recipes, supported, candidate ->
                candidate.recipeId().equals(plan.recipeId()) && candidate.processorType().equals(plan.processorType())
                        || executable == null || executable.test(candidate), null, candidate -> 0,
                (recipe, idx) -> 0L);
        State state = new State(new LinkedHashMap<>(available), new ArrayList<>());
        planner.rankStockRoutes(state.available, ingredients.stream()
                .flatMap(ingredient -> ingredient.alternatives().stream()).toList());
        State planned = planner.inputs(def, 0, 1L, state, new HashSet<>(Set.of(plan.result())),
                new ArrayList<>(), 0, Function.identity());
        if(planned == null) return new WorkerRecipeChain(List.of());
        WorkerRecipeChain grouped = new WorkerRecipeChain(planned.steps).grouped();
        return new WorkerRecipeChain(grouped.steps().subList(0, grouped.steps().size() - 1));
    }

    // Restore tag alternatives for queues saved before ingredient alternatives were persisted
    private static List<WorkerResourceKey> acceptedAlternatives(WorkerRecipePlan plan, WorkerRecipePlan.Input input,
                                                                WorkerRecipeIndex recipes){
        if(input.alternatives().size() > 1) return input.alternatives();
        List<List<WorkerResourceKey>> matches = recipes.producing(plan.result()).stream()
                .filter(recipe -> recipe.recipeId().equals(plan.recipeId())
                        && recipe.processorType().equals(plan.processorType())
                        && recipe.result().equals(plan.result()))
                .flatMap(recipe -> recipe.ingredients().stream())
                .filter(ingredient -> ingredient.alternatives().contains(input.resource()))
                .map(WorkerRecipeDefinition.Ingredient::alternatives).distinct().toList();
        return matches.size() == 1 ? matches.getFirst() : input.alternatives();
    }

    // Compare complete schedules before committing inventory reservations
    private State produce(WorkerResourceKey result, long missing, State state, Set<WorkerResourceKey> visiting,
                          WorkerRecipePlan.Operation operation, int depth, Function<State, State> complete){
        if(depth >= MAX_DEPTH || ++branches > MAX_BRANCHES){
            failure = new WorkerFailureReason("recipe_search_limit", "Recipe search limit reached while resolving " + result.id());
            return null;
        }
        if(!visiting.add(result)) return null;
        try{
            if(recipes.producing(result).isEmpty()){
                failure = new WorkerFailureReason("recipe_input_unavailable",
                        "No stocked material or supported machine recipe supplies " + result.id());
                failureDetails.add("Ingredient: " + result.id() + " (no stock or producing recipe)");
            }
            State best = null;
            List<WorkerRecipeDefinition> choices = recipes.producing(result).stream().filter(this::supported)
                    .filter(def -> recipeCost(def, missing, state) < UNREACHABLE)
                    .sorted(Comparator.comparingInt((WorkerRecipeDefinition def) -> recipeCost(def, missing, state))
                            .thenComparingLong(def -> stockedDeficit(def, missing, state))
                            .thenComparingInt(def -> def.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING
                                    ? 0 : 1)
                            .thenComparingInt(def -> def.ingredients().size())
                            .thenComparing(def -> def.recipeId().toString())).toList();
            if(choices.isEmpty() && !recipes.producing(result).isEmpty()){
                for(WorkerRecipeDefinition def : recipes.producing(result)){
                    if(!supported(def)){
                        failureDetails.add("Machine route: " + def.processorType() + " for " + def.recipeId());
                        continue;
                    }
                    for(WorkerRecipeDefinition.Ingredient ingredient : def.ingredients()){
                        long stocked = ingredient.alternatives().stream()
                                .mapToLong(key -> state.available.getOrDefault(key, 0L)).sum();
                        if(stocked >= ingredient.amount() || ingredient.alternatives().stream()
                                .anyMatch(key -> productionCosts.getOrDefault(key, UNREACHABLE) < UNREACHABLE)) continue;
                        for(WorkerResourceKey alternative : ingredient.alternatives().stream().limit(3).toList())
                            explainUnavailable(alternative, ingredient.amount(), state, new HashSet<>(), 0);
                    }
                }
            }
            Map<DemandKey, TerminalDemand> terminalDemand = new HashMap<>();
            for(WorkerRecipeDefinition def : choices){
                if(!matches(def.operation(), operation) || def.ingredients().isEmpty()) continue;
                long crafts = (missing - 1L) / def.resultAmount() + 1L;
                if(crafts > Long.MAX_VALUE / def.resultAmount()) continue;
                if(exceedsTerminalStock(def, crafts, state, terminalDemand)) continue;
                State candidate = inputs(def, 0, crafts, state.copy(), visiting, new ArrayList<>(), depth, resolved -> {
                    resolved.available.merge(result, crafts * def.resultAmount(), WorkerRecipePlanner::add);
                    visiting.remove(result);
                    try{
                        return complete.apply(resolved);
                    }finally{
                        visiting.add(result);
                    }
                });
                best = better(best, candidate);
                if(best != null && best.steps.size() == state.steps.size() + 1
                        && routePenalty(best) == 0L && processingPenalty(best) == 0L) break;
            }
            return best;
        }finally{
            visiting.remove(result);
        }
    }

    // Backtrack ingredient alternatives when a later slot needs an already reserved resource
    private State inputs(WorkerRecipeDefinition def, int idx, long crafts, State state,
                         Set<WorkerResourceKey> visiting, List<WorkerRecipePlan.Input> selected, int depth,
                         Function<State, State> complete){
        if(++branches > MAX_BRANCHES){
            failure = new WorkerFailureReason("recipe_search_limit", "Recipe search limit reached while resolving " + def.result().id());
            return null;
        }
        if(idx >= def.ingredients().size()){
            WorkerRecipePlan plan = new WorkerRecipePlan(def.recipeId(), def.processorType(), def.operation(),
                    selected, def.result(), def.resultAmount());
            if(executable != null && !executable.test(plan)){
                failureDetails.add("Machine route: " + plan.processorType() + " for " + plan.recipeId());
                return null;
            }
            if(depth == 0 && rootExecutable != null && !rootExecutable.test(plan)){
                failureDetails.add("Selected machine cannot run " + plan.recipeId());
                return null;
            }
            state.steps.add(new WorkerRecipeChain.Step(plan, crafts * def.resultAmount()));
            return complete.apply(state);
        }
        WorkerRecipeDefinition.Ingredient ingredient = def.ingredients().get(idx);
        if(crafts > Long.MAX_VALUE / ingredient.amount()) return null;
        long needed = ingredient.amount() * crafts - machineCredit(def, idx, crafts);
        if(needed == 0L){
            List<WorkerRecipePlan.Input> next = new ArrayList<>(selected);
            next.add(new WorkerRecipePlan.Input(ingredient.alternatives().getFirst(), ingredient.amount(),
                    ingredient.alternatives()));
            return inputs(def, idx + 1, crafts, state, visiting, next, depth, complete);
        }
        List<WorkerResourceKey> alternatives = ingredient.alternatives().stream()
                .sorted(Comparator.comparingInt((WorkerResourceKey key) -> inputCost(key, needed, state))
                        .thenComparing(Comparator.comparingLong((WorkerResourceKey key) ->
                                state.available.getOrDefault(key, 0L)).reversed())).toList();
        long combined = 0L;
        for(WorkerResourceKey resource : alternatives){
            combined = add(combined, Math.max(0L, state.available.getOrDefault(resource, 0L)));
        }
        State best = null;
        if(alternatives.size() > 1 && combined >= needed){
            List<WorkerResourceKey> stocked = alternatives.stream()
                    .filter(resource -> state.available.getOrDefault(resource, 0L) > 0L).toList();
            Set<WorkerResourceKey> exactInputs = new HashSet<>();
            for(WorkerRecipeDefinition.Ingredient slot : def.ingredients()){
                if(slot.alternatives().size() == 1) exactInputs.add(slot.alternatives().getFirst());
            }
            WorkerResourceKey representative = stocked.stream()
                    .filter(resource -> !exactInputs.contains(resource)
                            && state.available.getOrDefault(resource, 0L) > 0L)
                    .findFirst().orElse(stocked.getFirst());
            List<WorkerRecipePlan.Input> next = new ArrayList<>(selected);
            next.add(new WorkerRecipePlan.Input(representative, ingredient.amount(), ingredient.alternatives()));
            State shared = reserveAlternatives(stocked, 0, needed, state.copy(), reserved ->
                    inputs(def, idx + 1, crafts, reserved, visiting, next, depth, complete));
            best = better(best, shared);
        }
        Map<SupplyKey, Boolean> possible = new HashMap<>();
        if(alternatives.size() > 1 && combined < needed){
            long shortage = needed - combined;
            for(WorkerResourceKey resource : alternatives){
                if(!productionCosts.containsKey(resource)) continue;
                long target = add(Math.max(0L, state.available.getOrDefault(resource, 0L)), shortage);
                if(!canSupply(resource, target, state, new HashSet<>(visiting), 0, possible)) continue;
                List<WorkerRecipePlan.Input> next = new ArrayList<>(selected);
                next.add(new WorkerRecipePlan.Input(resource, ingredient.amount(), ingredient.alternatives()));
                State supplied = produce(resource, shortage, state.copy(), visiting, null, depth + 1,
                        produced -> reserveAlternatives(alternatives, 0, needed, produced,
                                reserved -> inputs(def, idx + 1, crafts, reserved, visiting, next, depth, complete)));
                best = better(best, supplied);
            }
        }
        for(WorkerResourceKey resource : alternatives){
            long stored = Math.max(0L, state.available.getOrDefault(resource, 0L));
            if(stored < needed && !productionCosts.containsKey(resource)) continue;
            if(stored < needed && !canSupply(resource, needed, state, new HashSet<>(visiting), 0, possible)) continue;
            State candidate = state.copy();
            List<WorkerRecipePlan.Input> next = new ArrayList<>(selected);
            next.add(new WorkerRecipePlan.Input(resource, ingredient.amount(), ingredient.alternatives()));
            Function<State, State> reserve = supplied -> {
                long count = supplied.available.getOrDefault(resource, 0L);
                if(count < needed) return null;
                supplied.available.put(resource, count - needed);
                return inputs(def, idx + 1, crafts, supplied, visiting, next, depth, complete);
            };
            State resolved = stored < needed
                    ? produce(resource, needed - stored, candidate, visiting, null, depth + 1, reserve)
                    : reserve.apply(candidate);
            best = better(best, resolved);
        }
        return best;
    }

    // Reject an input only when no supported route can cover its missing amount
    private boolean canSupply(WorkerResourceKey resource, long needed, State state,
                               Set<WorkerResourceKey> visiting, int depth, Map<SupplyKey, Boolean> known){
        if(state.available.getOrDefault(resource, 0L) >= needed) return true;
        if(++supplyChecks > MAX_GRAPH_CHECKS) return true;
        if(depth >= MAX_DEPTH || !visiting.add(resource)) return false;
        SupplyKey key = new SupplyKey(resource, needed, Set.copyOf(visiting));
        try{
            Boolean cached = known.get(key);
            if(cached != null) return cached;
            long missing = needed - Math.max(0L, state.available.getOrDefault(resource, 0L));
            for(WorkerRecipeDefinition def : recipes.producing(resource)){
                if(!supported(def) || def.ingredients().isEmpty()) continue;
                long crafts = (missing - 1L) / def.resultAmount() + 1L;
                boolean possible = true;
                for(int idx = 0; idx < def.ingredients().size(); idx++){
                    WorkerRecipeDefinition.Ingredient ingredient = def.ingredients().get(idx);
                    if(crafts > Long.MAX_VALUE / ingredient.amount()){
                        possible = false;
                        break;
                    }
                    long required = crafts * ingredient.amount() - machineCredit(def, idx, crafts);
                    if(required <= 0L) continue;
                    long stocked = 0L;
                    for(WorkerResourceKey alternative : ingredient.alternatives()){
                        stocked = add(stocked, Math.max(0L, state.available.getOrDefault(alternative, 0L)));
                    }
                    if(stocked >= required) continue;
                    boolean supplied = false;
                    for(WorkerResourceKey alternative : ingredient.alternatives()){
                        long amount = Math.max(0L, state.available.getOrDefault(alternative, 0L));
                        long target = ingredient.alternatives().size() == 1 ? required : add(amount, 1L);
                        if(canSupply(alternative, target, state, visiting, depth + 1, known)){
                            supplied = true;
                            break;
                        }
                    }
                    if(!supplied){
                        possible = false;
                        break;
                    }
                }
                if(possible){
                    known.put(key, true);
                    return true;
                }
            }
            known.put(key, false);
            return false;
        }finally{
            visiting.remove(resource);
        }
    }

    private record SupplyKey(WorkerResourceKey resource, long needed, Set<WorkerResourceKey> visiting){}

    // Diagnose the first blocked leaf of the cached dependency graph after a failed request.
    private void explainUnavailable(WorkerResourceKey resource, long needed, State state,
                                    Set<WorkerResourceKey> visiting, int depth){
        if(failureDetails.size() >= 12 || depth >= MAX_DEPTH || !visiting.add(resource)) return;
        try{
            long stocked = Math.max(0L, state.available.getOrDefault(resource, 0L));
            if(stocked >= needed) return;
            List<WorkerRecipeDefinition> definitions = recipes.producing(resource);
            if(definitions.isEmpty()){
                recordFailureDetail("Ingredient: " + resource.id() + " needs " + needed
                        + ", linked stock " + stocked + " (no producing recipe)");
                return;
            }
            List<WorkerRecipeDefinition> supportedRecipes = definitions.stream().filter(this::supported).toList();
            if(supportedRecipes.isEmpty()){
                definitions.stream().limit(3).forEach(def -> recordFailureDetail("Machine route: "
                        + def.processorType() + " for " + def.recipeId()));
                return;
            }
            int before = failureDetails.size();
            for(WorkerRecipeDefinition def : supportedRecipes.stream().limit(3).toList()){
                long missing = needed - stocked;
                long crafts = (missing - 1L) / def.resultAmount() + 1L;
                for(int idx = 0; idx < def.ingredients().size(); idx++){
                    WorkerRecipeDefinition.Ingredient ingredient = def.ingredients().get(idx);
                    long required = crafts > Long.MAX_VALUE / ingredient.amount() ? Long.MAX_VALUE
                            : crafts * ingredient.amount();
                    required = Math.max(0L, required - machineCredit(def, idx, crafts));
                    long present = ingredient.alternatives().stream()
                            .mapToLong(key -> Math.max(0L, state.available.getOrDefault(key, 0L)))
                            .reduce(0L, WorkerRecipePlanner::add);
                    if(present >= required || ingredient.alternatives().stream().anyMatch(key ->
                            productionCosts.getOrDefault(key, UNREACHABLE) < UNREACHABLE)) continue;
                    for(WorkerResourceKey alternative : ingredient.alternatives().stream().limit(3).toList())
                        explainUnavailable(alternative, required, state, visiting, depth + 1);
                    if(failureDetails.size() > before) return;
                }
            }
            if(failureDetails.size() == before) recordFailureDetail("Recipe schedule: " + resource.id()
                    + " has ingredient routes, but their combined stock cannot complete the request");
        }finally{
            visiting.remove(resource);
        }
    }

    // Reject recipes whose unavoidable terminal inputs exceed the stock shared by their branches
    private boolean exceedsTerminalStock(WorkerRecipeDefinition def, long crafts, State state,
                                         Map<DemandKey, TerminalDemand> known){
        TerminalDemand demand = recipeTerminalDemand(def, crafts, state, new HashSet<>(), known);
        if(!demand.possible()){
            Map<List<WorkerResourceKey>, Long> terminal = new LinkedHashMap<>();
            for(int idx = 0; idx < def.ingredients().size(); idx++){
                WorkerRecipeDefinition.Ingredient ingredient = def.ingredients().get(idx);
                long needed = crafts > Long.MAX_VALUE / ingredient.amount() ? Long.MAX_VALUE
                        : crafts * ingredient.amount();
                needed = Math.max(0L, needed - machineCredit(def, idx, crafts));
                if(needed > 0L) terminal.merge(ingredient.alternatives(), needed, WorkerRecipePlanner::add);
            }
            for(var entry : terminal.entrySet()){
                long stocked = entry.getKey().stream().mapToLong(key ->
                        Math.max(0L, state.available.getOrDefault(key, 0L))).reduce(0L, WorkerRecipePlanner::add);
                if(stocked >= entry.getValue() || entry.getKey().stream().anyMatch(key ->
                        recipes.producing(key).stream().anyMatch(this::supported))) continue;
                for(WorkerResourceKey alternative : entry.getKey().stream().limit(3).toList())
                    explainUnavailable(alternative, entry.getValue(), state, new HashSet<>(), 0);
            }
            if(failureDetails.isEmpty()) recordFailureDetail("Recipe inputs: " + def.recipeId()
                    + " have no stocked or executable supply route");
            return true;
        }
        boolean shortOfStock = false;
        for(var entry : demand.amounts().entrySet()){
            long stocked = Math.max(0L, state.available.getOrDefault(entry.getKey(), 0L));
            if(entry.getValue() <= stocked) continue;
            recordFailureDetail("Ingredient: " + entry.getKey().id() + " needs " + entry.getValue()
                    + ", linked stock " + stocked);
            shortOfStock = true;
        }
        return shortOfStock;
    }

    // Trace the minimum amount of each terminal resource required by a cached recipe route
    private TerminalDemand terminalDemand(WorkerResourceKey resource, long needed, State state,
                                           Set<WorkerResourceKey> visiting, Map<DemandKey, TerminalDemand> known){
        if(needed <= 0L) return TerminalDemand.EMPTY;
        if(++demandChecks > MAX_GRAPH_CHECKS) return TerminalDemand.UNCERTAIN;
        List<WorkerRecipeDefinition> choices = recipes.producing(resource).stream()
                .filter(this::supported).toList();
        if(choices.isEmpty()){
            long stocked = Math.max(0L, state.available.getOrDefault(resource, 0L));
            if(stocked < needed) return TerminalDemand.IMPOSSIBLE;
            return new TerminalDemand(Map.of(resource, needed), Set.of());
        }
        if(visiting.size() >= MAX_DEPTH) return TerminalDemand.UNCERTAIN;
        if(state.available.getOrDefault(resource, 0L) >= needed) return TerminalDemand.EMPTY;
        if(!visiting.add(resource)) return TerminalDemand.UNCERTAIN;
        DemandKey key = new DemandKey(resource, needed);
        try{
            TerminalDemand cached = known.get(key);
            if(cached != null) return cached;
            long missing = needed - Math.max(0L, state.available.getOrDefault(resource, 0L));
            TerminalDemand minimum = null;
            for(WorkerRecipeDefinition def : choices){
                long crafts = (missing - 1L) / def.resultAmount() + 1L;
                TerminalDemand candidate = recipeTerminalDemand(def, crafts, state, visiting, known);
                if(!candidate.possible()) continue;
                minimum = minimum == null ? candidate : lowerDemand(minimum, candidate);
                if(minimum.amounts().isEmpty()) break;
            }
            if(minimum == null){
                known.put(key, TerminalDemand.IMPOSSIBLE);
                return TerminalDemand.IMPOSSIBLE;
            }
            Set<WorkerResourceKey> intermediate = new HashSet<>(minimum.intermediates());
            intermediate.add(resource);
            TerminalDemand resolved = new TerminalDemand(minimum.amounts(), Set.copyOf(intermediate),
                    true, minimum.cacheable());
            if(resolved.cacheable()) known.put(key, resolved);
            return resolved;
        }finally{
            visiting.remove(resource);
        }
    }

    private TerminalDemand recipeTerminalDemand(WorkerRecipeDefinition def, long crafts, State state,
                                                Set<WorkerResourceKey> visiting,
                                                Map<DemandKey, TerminalDemand> known){
        Map<List<WorkerResourceKey>, Long> grouped = new LinkedHashMap<>();
        for(int idx = 0; idx < def.ingredients().size(); idx++){
            WorkerRecipeDefinition.Ingredient ingredient = def.ingredients().get(idx);
            if(crafts > Long.MAX_VALUE / ingredient.amount()) return TerminalDemand.IMPOSSIBLE;
            long needed = crafts * ingredient.amount() - machineCredit(def, idx, crafts);
            if(needed > 0L) grouped.merge(ingredient.alternatives(), needed, WorkerRecipePlanner::add);
        }
        Map<WorkerResourceKey, Long> demand = new HashMap<>();
        Set<WorkerResourceKey> intermediate = new HashSet<>();
        boolean cacheable = true;
        for(var group : grouped.entrySet()){
            TerminalDemand minimum = null;
            if(group.getKey().size() > 1){
                minimum = taggedTerminalDemand(group.getKey(), group.getValue(), state, visiting);
            }else{
                for(WorkerResourceKey alternative : group.getKey()){
                    TerminalDemand candidate = terminalDemand(alternative, group.getValue(), state, visiting, known);
                    if(!candidate.possible()) continue;
                    minimum = minimum == null ? candidate : lowerDemand(minimum, candidate);
                    if(minimum.amounts().isEmpty()) break;
                }
            }
            if(minimum == null) return TerminalDemand.IMPOSSIBLE;
            if(!minimum.possible()) return TerminalDemand.IMPOSSIBLE;
            boolean sharesIntermediate = minimum.intermediates().stream().anyMatch(intermediate::contains);
            minimum.amounts().forEach((resource, amount) -> demand.merge(resource, amount,
                    sharesIntermediate ? Math::max : WorkerRecipePlanner::add));
            intermediate.addAll(minimum.intermediates());
            cacheable &= minimum.cacheable();
        }
        return new TerminalDemand(Map.copyOf(demand), Set.copyOf(intermediate), true, cacheable);
    }

    // Count only the stock a tag must consume regardless of which accepted member is chosen
    private TerminalDemand taggedTerminalDemand(List<WorkerResourceKey> alternatives, long required, State state,
                                                 Set<WorkerResourceKey> visiting){
        Map<SupplyKey, Boolean> known = new HashMap<>();
        long stocked = 0L;
        for(WorkerResourceKey resource : alternatives){
            stocked = add(stocked, Math.max(0L, state.available.getOrDefault(resource, 0L)));
        }
        boolean pathDependent = false;
        long shortage = Math.max(0L, required - stocked);
        for(WorkerResourceKey resource : alternatives){
            if(recipes.producing(resource).stream().noneMatch(this::supported)) continue;
            pathDependent = true;
            long target = add(Math.max(0L, state.available.getOrDefault(resource, 0L)), Math.max(1L, shortage));
            if(canSupply(resource, target, state, new HashSet<>(visiting), 0, known))
                return TerminalDemand.UNCERTAIN;
        }
        if(stocked < required){
            return new TerminalDemand(Map.of(), Set.of(), false, !pathDependent);
        }
        Map<WorkerResourceKey, Long> forced = new HashMap<>();
        for(WorkerResourceKey resource : alternatives){
            long amount = Math.max(0L, state.available.getOrDefault(resource, 0L));
            long other = stocked == Long.MAX_VALUE ? Long.MAX_VALUE : stocked - amount;
            long minimum = Math.max(0L, required - other);
            if(minimum > 0L) forced.put(resource, minimum);
        }
        return new TerminalDemand(Map.copyOf(forced), Set.of(), true, !pathDependent);
    }

    private static TerminalDemand lowerDemand(TerminalDemand first, TerminalDemand second){
        Map<WorkerResourceKey, Long> lower = new HashMap<>();
        first.amounts().forEach((resource, amount) -> {
            long other = second.amounts().getOrDefault(resource, 0L);
            if(other > 0L) lower.put(resource, Math.min(amount, other));
        });
        Set<WorkerResourceKey> intermediate = new HashSet<>(first.intermediates());
        intermediate.addAll(second.intermediates());
        return new TerminalDemand(Map.copyOf(lower), Set.copyOf(intermediate), true,
                first.cacheable() && second.cacheable());
    }

    private record TerminalDemand(Map<WorkerResourceKey, Long> amounts,
                                  Set<WorkerResourceKey> intermediates, boolean possible, boolean cacheable){
        private TerminalDemand(Map<WorkerResourceKey, Long> amounts, Set<WorkerResourceKey> intermediates){
            this(amounts, intermediates, true, true);
        }
        private static final TerminalDemand EMPTY = new TerminalDemand(Map.of(), Set.of());
        private static final TerminalDemand UNCERTAIN = new TerminalDemand(Map.of(), Set.of(), true, false);
        private static final TerminalDemand IMPOSSIBLE = new TerminalDemand(Map.of(), Set.of(), false, true);
    }

    private record DemandKey(WorkerResourceKey resource, long amount){}

    // Reserve a tag ingredient across its stocked variants while backtracking later exact needs
    private State reserveAlternatives(List<WorkerResourceKey> alternatives, int idx, long needed,
                                      State state, Function<State, State> complete){
        if(needed <= 0L) return complete.apply(state);
        if(idx >= alternatives.size() || ++branches > MAX_BRANCHES) return null;
        WorkerResourceKey resource = alternatives.get(idx);
        long stored = Math.max(0L, state.available.getOrDefault(resource, 0L));
        long later = 0L;
        for(int next = idx + 1; next < alternatives.size(); next++){
            later = add(later, Math.max(0L, state.available.getOrDefault(alternatives.get(next), 0L)));
        }
        long minimum = Math.max(0L, needed - Math.min(needed, later));
        long maximum = Math.min(needed, stored);
        State best = null;
        for(long take = maximum; take >= minimum; take--){
            State branch = state.copy();
            branch.available.put(resource, stored - take);
            State resolved = reserveAlternatives(alternatives, idx + 1, needed - take, branch, complete);
            best = better(best, resolved);
            if(branches > MAX_BRANCHES || take == 0L) break;
        }
        return best;
    }

    // Prefer fewer visits, direct crafting and no transport isolation, then less material handling
    private State better(State first, State second){
        if(second == null) return first;
        if(first == null) return second;
        long firstCost = first.steps.size() + routePenalty(first) + processingPenalty(first);
        long secondCost = second.steps.size() + routePenalty(second) + processingPenalty(second);
        if(firstCost != secondCost) return firstCost < secondCost ? first : second;
        long firstPortable = first.steps.stream().filter(step ->
                step.plan().operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING).count();
        long secondPortable = second.steps.stream().filter(step ->
                step.plan().operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING).count();
        if(firstPortable != secondPortable) return firstPortable > secondPortable ? first : second;
        long firstAmount = first.steps.stream().mapToLong(WorkerRecipeChain.Step::requestedAmount)
                .reduce(0L, WorkerRecipePlanner::add);
        long secondAmount = second.steps.stream().mapToLong(WorkerRecipeChain.Step::requestedAmount)
                .reduce(0L, WorkerRecipePlanner::add);
        return firstAmount <= secondAmount ? first : second;
    }

    private long routePenalty(State state){
        return state.steps.stream().mapToLong(step -> Math.max(0, routingPenalty.applyAsInt(step.plan()))).sum();
    }

    // Prefer direct crafting when a processing line and crafting station can both make the result
    private static long processingPenalty(State state){
        return state.steps.stream().filter(step -> step.plan().operation() == WorkerRecipePlan.Operation.PROCESSING)
                .count();
    }

    // Rank routes seeded by stocked materials before exploring ingredient alternatives
    private void rankStockRoutes(Map<WorkerResourceKey, Long> stocked, Collection<WorkerResourceKey> outputs){
        routeCosts.clear();
        productionCosts.clear();
        PriorityQueue<Route> pending = new PriorityQueue<>(Comparator.comparingInt(Route::cost));
        stocked.forEach((resource, amount) -> {
            if(amount > 0L){
                routeCosts.put(resource, 0);
                pending.add(new Route(resource, 0));
            }
        });
        Set<WorkerRecipeDefinition> related = new HashSet<>(recipes.dependencies(outputs));
        for(WorkerRecipeDefinition def : related){
            if(!supported(def) || def.ingredients().isEmpty()) continue;
            boolean loaded = true;
            for(int idx = 0; idx < def.ingredients().size(); idx++){
                if(machineCredit(def, idx, 1L) < def.ingredients().get(idx).amount()){
                    loaded = false;
                    break;
                }
            }
            if(loaded){
                productionCosts.put(def.result(), 1);
                if(routeCosts.putIfAbsent(def.result(), 1) == null) pending.add(new Route(def.result(), 1));
            }
        }
        while(!pending.isEmpty()){
            Route route = pending.poll();
            if(route.cost() != routeCosts.getOrDefault(route.resource(), UNREACHABLE)) continue;
            for(WorkerRecipeDefinition def : recipes.consuming(route.resource())){
                if(!related.contains(def) || def.ingredients().isEmpty() || !supported(def)) continue;
                int cost = 1;
                for(int idx = 0; idx < def.ingredients().size(); idx++){
                    WorkerRecipeDefinition.Ingredient ingredient = def.ingredients().get(idx);
                    if(machineCredit(def, idx, 1L) >= ingredient.amount()) continue;
                    int input = ingredient.alternatives().stream()
                            .mapToInt(resource -> routeCosts.getOrDefault(resource, UNREACHABLE))
                            .min().orElse(UNREACHABLE);
                    cost = (int)Math.min(UNREACHABLE, (long)cost + input);
                }
                if(cost >= UNREACHABLE) continue;
                productionCosts.merge(def.result(), cost, Math::min);
                if(cost < routeCosts.getOrDefault(def.result(), UNREACHABLE)){
                    routeCosts.put(def.result(), cost);
                    pending.add(new Route(def.result(), cost));
                }
            }
        }
    }

    private record Route(WorkerResourceKey resource, int cost){}

    // Prefer stock that can satisfy the full amount, then the shortest stocked production route
    private int inputCost(WorkerResourceKey resource, long needed, State state){
        return state.available.getOrDefault(resource, 0L) >= needed ? 0
                : productionCosts.getOrDefault(resource, UNREACHABLE);
    }

    private int recipeCost(WorkerRecipeDefinition def, long missing, State state){
        long crafts = (missing - 1L) / def.resultAmount() + 1L;
        int cost = 1;
        for(int idx = 0; idx < def.ingredients().size(); idx++){
            WorkerRecipeDefinition.Ingredient ingredient = def.ingredients().get(idx);
            long needed = crafts > Long.MAX_VALUE / ingredient.amount()
                    ? Long.MAX_VALUE : crafts * ingredient.amount();
            needed = Math.max(0L, needed - machineCredit(def, idx, crafts));
            if(needed == 0L) continue;
            long stocked = 0L;
            for(WorkerResourceKey resource : ingredient.alternatives())
                stocked = add(stocked, Math.max(0L, state.available.getOrDefault(resource, 0L)));
            int input = stocked >= needed ? 0 : ingredient.alternatives().stream()
                    .mapToInt(resource -> productionCosts.getOrDefault(resource, UNREACHABLE))
                    .min().orElse(UNREACHABLE);
            cost = Math.min(UNREACHABLE, cost + input);
        }
        return cost;
    }

    // Search recipes whose required ingredients are already stocked before expanding missing branches
    private long stockedDeficit(WorkerRecipeDefinition def, long missing, State state){
        long crafts = (missing - 1L) / def.resultAmount() + 1L;
        long deficit = 0L;
        for(int idx = 0; idx < def.ingredients().size(); idx++){
            WorkerRecipeDefinition.Ingredient ingredient = def.ingredients().get(idx);
            long stocked = 0L;
            for(WorkerResourceKey resource : ingredient.alternatives())
                stocked = add(stocked, Math.max(0L, state.available.getOrDefault(resource, 0L)));
            long needed = crafts > Long.MAX_VALUE / ingredient.amount()
                    ? Long.MAX_VALUE : crafts * ingredient.amount();
            needed = Math.max(0L, needed - machineCredit(def, idx, crafts));
            deficit = add(deficit, Math.max(0L, needed - stocked));
        }
        return deficit;
    }

    // Accept either crafting location when the consumer requests crafting
    private static boolean matches(WorkerRecipePlan.Operation actual, WorkerRecipePlan.Operation requested){
        return requested == null || actual == requested || requested == WorkerRecipePlan.Operation.CRAFTING
                && actual == WorkerRecipePlan.Operation.WORKER_CRAFTING;
    }

    // Keep inventory totals within the persistent amount range
    private static long add(long first, long second){
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    // Isolate stock reservations and completed prerequisites for one search branch
    private record State(Map<WorkerResourceKey, Long> available, List<WorkerRecipeChain.Step> steps){
        // Copy reservations before exploring an alternative
        private State copy(){
            return new State(new LinkedHashMap<>(available), new ArrayList<>(steps));
        }
    }

    public record Result(WorkerRecipeChain chain, WorkerFailureReason failure, List<String> details){
        public Result(WorkerRecipeChain chain, WorkerFailureReason failure){ this(chain, failure, List.of()); }
    }
}
