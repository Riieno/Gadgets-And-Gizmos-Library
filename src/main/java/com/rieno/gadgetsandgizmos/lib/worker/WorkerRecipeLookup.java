package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.util.DeferredLookup;
import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

// Cache detached planning results while querying live machine routes within the shared tick budget
public final class WorkerRecipeLookup{
    private final DeferredLookup<Request, WorkerRecipePlanner.Result> plans = new DeferredLookup<>(64,
            Long.MAX_VALUE / 2, res -> res.chain().executable() ? Long.MAX_VALUE / 2 : 20L);
    private final DeferredLookup<Repair, WorkerRecipeChain> repairs = new DeferredLookup<>(64,
            Long.MAX_VALUE / 2, res -> res.executable() ? Long.MAX_VALUE / 2 : 20L);
    private final DeferredLookup<Output, Set<WorkerResourceKey>> resources = new DeferredLookup<>(128, Long.MAX_VALUE / 2);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Defer dependency discovery before filtering inventory snapshots to relevant resources
    public Set<WorkerResourceKey> relevantResources(DeferredWorkScheduler scheduler, WorkerRecipeIndex index,
                                                   WorkerResourceKey output){
        return resources.get(scheduler, new Output(scheduler, index, output), () -> index.relevantResources(output));
    }

    // Keep callbacks on the owning thread; context identifies the consumer's machine and tool configuration
    public WorkerRecipePlanner.Result plan(DeferredWorkScheduler scheduler, WorkerRecipeIndex index,
                                            WorkerResourceKey output, long amount,
                                            Map<WorkerResourceKey, Long> available, WorkerRecipePlan.Operation operation,
                                            Object context, RecipeSupport supported,
                                            Predicate<WorkerRecipePlan> executable, Predicate<WorkerRecipePlan> root,
                                            ToIntFunction<WorkerRecipePlan> routing,
                                            WorkerRecipePlanner.IngredientStock machineStock){
        return plan(scheduler, index, null, output, amount, available, operation, context,
                supported, executable, root, routing, machineStock);
    }

    // Keep the first usable recipe schedule without discovering every possible ingredient tree
    public WorkerRecipePlanner.Result planFromStock(DeferredWorkScheduler scheduler, WorkerRecipeSource source,
                                                     WorkerResourceKey output, long amount,
                                                     Map<WorkerResourceKey, Long> available,
                                                     WorkerRecipePlan.Operation operation, Object context,
                                                     RecipeSupport supported, Predicate<WorkerRecipePlan> executable,
                                                     Predicate<WorkerRecipePlan> root,
                                                     ToIntFunction<WorkerRecipePlan> routing,
                                                     WorkerRecipePlanner.IngredientStock machineStock){
        if(source == null) throw new IllegalArgumentException("Worker planning needs a recipe source");
        return plan(scheduler, null, source, output, amount, available, operation, context,
                supported, executable, root, routing, machineStock);
    }

    private WorkerRecipePlanner.Result plan(DeferredWorkScheduler scheduler, WorkerRecipeIndex index,
                                             WorkerRecipeSource source, WorkerResourceKey output, long amount,
                                             Map<WorkerResourceKey, Long> available,
                                             WorkerRecipePlan.Operation operation, Object context,
                                             RecipeSupport supported, Predicate<WorkerRecipePlan> executable,
                                             Predicate<WorkerRecipePlan> root,
                                             ToIntFunction<WorkerRecipePlan> routing,
                                             WorkerRecipePlanner.IngredientStock machineStock){
        Map<WorkerResourceKey, Long> snapshot = Map.copyOf(available);
        Object recipes = source == null ? index : source;
        Request request = new Request(scheduler, recipes, output, amount, snapshot, operation, context);
        java.util.function.Supplier<WorkerRecipePlanner.Result> task = () -> {
            Map<WorkerRecipePlan, Boolean> routes = new HashMap<>();
            Map<WorkerRecipePlan, Boolean> finalRoutes = new HashMap<>();
            Map<WorkerRecipePlan, Integer> costs = new HashMap<>();
            Map<WorkerRecipeDefinition, List<Long>> stock = new IdentityHashMap<>();
            Predicate<WorkerRecipeDefinition> supportedRecipe = def -> supported.test(def, scheduler);
            Predicate<WorkerRecipePlan> executablePlan = plan -> routes.computeIfAbsent(plan,
                    val -> scheduler.onOwnerThread(() -> executable.test(val)));
            Predicate<WorkerRecipePlan> finalPlan = root == null ? null : plan -> finalRoutes.computeIfAbsent(plan,
                    val -> scheduler.onOwnerThread(() -> root.test(val)));
            ToIntFunction<WorkerRecipePlan> routingCost = plan -> costs.computeIfAbsent(plan,
                    val -> scheduler.onOwnerThread(() -> routing.applyAsInt(val)));
            WorkerRecipePlanner.IngredientStock machineInputs = (def, idx) -> stock.computeIfAbsent(def,
                    val -> scheduler.onOwnerThread(() -> {
                        List<Long> inputs = new java.util.ArrayList<>();
                        for(int slot = 0; slot < val.ingredients().size(); slot++)
                            inputs.add(machineStock.available(val, slot));
                        return List.copyOf(inputs);
                    })).get(idx);
            return source == null ? WorkerRecipePlanner.planDetailed(output, amount, snapshot, index, operation,
                    supportedRecipe, executablePlan, finalPlan, routingCost, machineInputs)
                    : WorkerRecipePlanner.planFromStock(output, amount, snapshot, source, operation,
                    supportedRecipe, executablePlan, finalPlan, routingCost, machineInputs);
        };
        if(context instanceof RequestContext ctx){
            request = new Request(scheduler, recipes, output, amount, request.available(), operation, ctx.state());
            Owner owner = new Owner(scheduler, recipes, ctx.requestId(),
                    List.of(output, amount, operation == null ? "automatic" : operation));
            return ctx.refresh() ? plans.refreshForRequest(scheduler, owner, request, task)
                    : plans.getForRequest(scheduler, owner, request, task);
        }
        return plans.get(scheduler, request, task);
    }

    // Reuse missing-input searches until stock, recipe progress or machine context changes
    public WorkerRecipeChain prerequisites(DeferredWorkScheduler scheduler, WorkerRecipeIndex index,
                                            WorkerRecipePlan plan, int inputIdx, long delivered, long batches,
                                            Map<WorkerResourceKey, Long> available, Object context,
                                            RecipeSupport supported,
                                            Predicate<WorkerRecipePlan> executable){
        return prerequisites(scheduler, index, null, plan, inputIdx, delivered, batches,
                available, context, supported, executable);
    }

    // Repair a saved step without looking up producers for ingredients that are already stocked
    public WorkerRecipeChain prerequisitesFromStock(DeferredWorkScheduler scheduler, WorkerRecipeSource source,
                                                     WorkerRecipePlan plan, int inputIdx, long delivered, long batches,
                                                     Map<WorkerResourceKey, Long> available, Object context,
                                                     RecipeSupport supported, Predicate<WorkerRecipePlan> executable){
        if(source == null) throw new IllegalArgumentException("Worker planning needs a recipe source");
        return prerequisites(scheduler, null, source, plan, inputIdx, delivered, batches,
                available, context, supported, executable);
    }

    private WorkerRecipeChain prerequisites(DeferredWorkScheduler scheduler, WorkerRecipeIndex index,
                                             WorkerRecipeSource source, WorkerRecipePlan plan, int inputIdx,
                                             long delivered, long batches, Map<WorkerResourceKey, Long> available,
                                             Object context, RecipeSupport supported,
                                             Predicate<WorkerRecipePlan> executable){
        Map<WorkerResourceKey, Long> snapshot = Map.copyOf(available);
        Object recipes = source == null ? index : source;
        Repair request = new Repair(scheduler, recipes, plan, inputIdx, delivered, batches, snapshot, context);
        java.util.function.Supplier<WorkerRecipeChain> task = () -> {
            Map<WorkerRecipePlan, Boolean> routes = new HashMap<>();
            Predicate<WorkerRecipeDefinition> supportedRecipe = def -> supported.test(def, scheduler);
            Predicate<WorkerRecipePlan> executablePlan = candidate -> routes.computeIfAbsent(candidate,
                    val -> scheduler.onOwnerThread(() -> executable.test(val)));
            return source == null ? WorkerRecipePlanner.prerequisites(plan, inputIdx, delivered, batches,
                    snapshot, index, supportedRecipe, executablePlan)
                    : WorkerRecipePlanner.prerequisitesFromStock(plan, inputIdx, delivered, batches,
                    snapshot, source, supportedRecipe, executablePlan);
        };
        if(context instanceof RequestContext ctx){
            request = new Repair(scheduler, recipes, plan, inputIdx, delivered, batches, request.available(), ctx.state());
            Owner owner = new Owner(scheduler, recipes, ctx.requestId(), List.of(plan, inputIdx, delivered, batches));
            return ctx.refresh() ? repairs.refreshForRequest(scheduler, owner, request, task)
                    : repairs.getForRequest(scheduler, owner, request, task);
        }
        return repairs.get(scheduler, request, task);
    }

    // Drop searches after a consumer is removed or its links are reconfigured
    public void clear(){
        plans.clear();
        repairs.clear();
        resources.clear();
    }

    public void cancel(Object requestId){
        plans.forgetRequests(key -> key instanceof Owner owner && owner.requestId().equals(requestId));
        repairs.forgetRequests(key -> key instanceof Owner owner && owner.requestId().equals(requestId));
    }

    // Keep an immutable routing context separate from the identity of the saved request being resumed
    public record RequestContext(Object requestId, Object state, boolean refresh){
        public RequestContext(Object requestId, Object state){ this(requestId, state, false); }
        public RequestContext{
            if(requestId == null) throw new IllegalArgumentException("Deferred recipe lookup needs a request identity");
        }
    }

    // Inspect detached definitions and schedule each live support query through the supplied owner
    @FunctionalInterface public interface RecipeSupport{
        boolean test(WorkerRecipeDefinition recipe, DeferredWorkScheduler scheduler);
    }

    private record Output(DeferredWorkScheduler scheduler, WorkerRecipeIndex index, WorkerResourceKey resource){}
    private record Owner(DeferredWorkScheduler scheduler, Object recipes, Object requestId, Object progress){}
    private record Request(DeferredWorkScheduler scheduler, Object recipes, WorkerResourceKey output,
                           long amount, Map<WorkerResourceKey, Long> available,
                           WorkerRecipePlan.Operation operation, Object context){}
    private record Repair(DeferredWorkScheduler scheduler, Object recipes, WorkerRecipePlan plan,
                          int inputIdx, long delivered, long batches,
                          Map<WorkerResourceKey, Long> available, Object context){}
}
