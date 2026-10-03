package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Map;

// Describe a loaded machine's actual recipe, input and output access
public interface WorkerMachine{
    boolean supports(WorkerRecipePlan plan);

    // Conservative type gate before a caller checks a machine's physical route.
    default boolean maySupportProcessor(ResourceLocation processorType){ return true; }
    default boolean maySupportRecipe(ResourceLocation recipeId, ResourceLocation processorType){
        return maySupportProcessor(processorType);
    }

    // Validate a recipe against the same area that will bound its runtime ports
    default boolean supports(WorkerRecipePlan plan, WorkerArea area){ return supports(plan); }
    default boolean supportsAt(WorkerRecipePlan plan, WorkerMachineSite site){
        return supports(plan, site == null ? null : site.area());
    }

    // Explain rejected physical routes only after a request has failed.
    default List<String> routeDiagnostics(WorkerRecipePlan plan, WorkerMachineSite site){ return List.of(); }

    // Reject unavailable machine types before the planner explores their ingredients
    default boolean supports(WorkerRecipeDefinition recipe){
        if(recipe.ingredients().stream().anyMatch(input -> input.alternatives().isEmpty())) return false;
        return supports(new WorkerRecipePlan(recipe.recipeId(), recipe.processorType(), recipe.operation(),
                recipe.ingredients().stream().map(input -> new WorkerRecipePlan.Input(
                        input.alternatives().getFirst(), input.amount(), input.alternatives())).toList(),
                recipe.result(), recipe.resultAmount()));
    }

    default boolean supports(WorkerRecipeDefinition recipe, WorkerArea area){
        if(area == null) return supports(recipe);
        if(recipe.ingredients().stream().anyMatch(input -> input.alternatives().isEmpty())) return false;
        return supports(new WorkerRecipePlan(recipe.recipeId(), recipe.processorType(), recipe.operation(),
                recipe.ingredients().stream().map(input -> new WorkerRecipePlan.Input(
                        input.alternatives().getFirst(), input.amount(), input.alternatives())).toList(),
                recipe.result(), recipe.resultAmount()), area);
    }
    default boolean supportsAt(WorkerRecipeDefinition recipe, WorkerMachineSite site){
        if(recipe == null || recipe.ingredients().stream().anyMatch(input -> input.alternatives().isEmpty()))
            return false;
        return supportsAt(new WorkerRecipePlan(recipe.recipeId(), recipe.processorType(), recipe.operation(),
                recipe.ingredients().stream().map(input -> new WorkerRecipePlan.Input(
                        input.alternatives().getFirst(), input.amount(), input.alternatives())).toList(),
                recipe.result(), recipe.resultAmount()), site);
    }

    default boolean virtualCrafting(){ return false; }
    default List<BlockPos> members(){ return List.of(); }
    default List<IItemHandler> itemInputs(WorkerRecipePlan plan){ return List.of(); }
    default List<IItemHandler> itemOutputs(WorkerRecipePlan plan){ return itemInputs(plan); }
    // Limit discovered machine ports to a linked worker area when one is assigned
    default List<IItemHandler> itemInputs(WorkerRecipePlan plan, WorkerArea area){ return itemInputs(plan); }
    // Enumerate every input that must be cleared before this machine can run another recipe.
    default List<IItemHandler> stagedItemInputs(WorkerRecipePlan plan, WorkerArea area){
        return itemInputs(plan, area);
    }
    default List<IItemHandler> stagedItemInputsAt(WorkerRecipePlan plan, WorkerMachineSite site){
        return itemInputsAt(plan, site);
    }
    default List<IItemHandler> itemOutputs(WorkerRecipePlan plan, WorkerArea area){ return itemOutputs(plan); }
    default List<IItemHandler> itemInputsAt(WorkerRecipePlan plan, WorkerMachineSite site){
        return itemInputs(plan, site == null ? null : site.area());
    }
    // Cheap conservative check before querying ingredients held by this machine.
    default boolean mayHavePreloadedInputs(WorkerRecipeDefinition recipe){ return true; }
    // Opt in only when preloadedInputs accepts recipes this machine cannot execute.
    default boolean mayProbePreloadedInputsBeforeSupport(){ return false; }
    // Report ingredients already present in each physical input for this recipe
    default List<Long> preloadedInputs(WorkerRecipePlan plan, WorkerArea area){ return List.of(); }
    default List<Long> preloadedInputs(WorkerRecipePlan plan, WorkerArea area, long batches){
        return preloadedInputs(plan, area);
    }
    default List<Long> preloadedInputsAt(WorkerRecipePlan plan, WorkerMachineSite site, long batches){
        return preloadedInputs(plan, site == null ? null : site.area(), batches);
    }
    default List<Long> preloadedInputs(WorkerRecipeDefinition recipe, WorkerArea area){
        if(recipe == null || recipe.ingredients().stream().anyMatch(input -> input.alternatives().isEmpty()))
            return List.of();
        WorkerRecipePlan plan = new WorkerRecipePlan(recipe.recipeId(), recipe.processorType(), recipe.operation(),
                recipe.ingredients().stream().map(input -> new WorkerRecipePlan.Input(
                        input.alternatives().getFirst(), input.amount(), input.alternatives())).toList(),
                recipe.result(), recipe.resultAmount());
        return preloadedInputs(plan, area);
    }
    default List<Long> preloadedInputsAt(WorkerRecipeDefinition recipe, WorkerMachineSite site){
        return preloadedInputs(recipe, site == null ? null : site.area());
    }
    // Select area containers reached by this machine's output route
    default List<BlockPos> areaOutputs(WorkerRecipePlan plan, WorkerArea area, List<BlockPos> candidates){
        return candidates;
    }
    // Expose completed intermediate passes that must be carried back to this machine's input
    default List<IItemHandler> recirculationOutputs(WorkerRecipePlan plan){ return List.of(); }
    default List<IItemHandler> recirculationOutputs(WorkerRecipePlan plan, WorkerArea area){
        return recirculationOutputs(plan);
    }
    // Expose transport gates that would divert this recipe's output through another route
    default List<BlockPos> conflictingTransport(WorkerRecipePlan plan, WorkerArea area){ return List.of(); }
    // Give routes that require temporary isolation a lower scheduling preference
    default int routingPenalty(WorkerRecipePlan plan, WorkerArea area){
        return conflictingTransport(plan, area).isEmpty() ? 0 : 8;
    }
    default List<IFluidHandler> fluidInputs(WorkerRecipePlan plan){ return List.of(); }
    default List<IFluidHandler> fluidOutputs(WorkerRecipePlan plan){ return fluidInputs(plan); }
    default boolean prepare(WorkerRecipePlan plan){ return supports(plan); }
    default void start(WorkerRecipePlan plan){}

    // Request machine supplies separately from recipe ingredients, such as furnace fuel
    default Supply supply(WorkerRecipePlan plan, Map<WorkerResourceKey, Long> available){ return Supply.READY; }

    // Size machine supplies for the production visit rather than one recipe operation
    default Supply supply(WorkerRecipePlan plan, Map<WorkerResourceKey, Long> available, long batches){
        return supply(plan, available);
    }

    record Supply(WorkerResourceKey resource, long amount, String status, boolean withdrawal){
        public Supply(WorkerResourceKey resource, long amount, String status){ this(resource, amount, status, false); }
        public static final Supply READY = new Supply(null, 0L, "");
        public boolean ready(){ return resource == null && status.isEmpty(); }
    }
}
