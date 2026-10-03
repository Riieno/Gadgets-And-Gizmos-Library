package com.rieno.gadgetsandgizmos.lib.create.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.worker.*;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Expand assembly loops into real machine visits while retaining Create's item progress components
public final class CreateWorkerSequences{
    private CreateWorkerSequences(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static WorkerRecipeDefinition definition(Level level, RecipeHolder<?> holder, SequencedAssemblyRecipe recipe){
        List<WorkerRecipeDefinition.Ingredient> inputs = new ArrayList<>();
        for(var step : recipe.getSequence()){
            var processing = step.getRecipe();
            for(int idx = 1; idx < processing.getIngredients().size(); idx++){
                inputs.add(ingredient(processing.getIngredients().get(idx), recipe.getLoops()));
            }
            for(var fluid : processing.getFluidIngredients()){
                inputs.add(new WorkerRecipeDefinition.Ingredient(Arrays.stream(fluid.getFluids())
                        .map(stack -> new WorkerResourceKey(WorkerResourceType.FLUID,
                                BuiltInRegistries.FLUID.getKey(stack.getFluid()))).toList(), (long)fluid.amount() * recipe.getLoops()));
            }
        }
        inputs.add(ingredient(recipe.getIngredient(), 1));
        ItemStack result = recipe.getResultItem(level.registryAccess());
        return new WorkerRecipeDefinition(holder.id(), BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()),
                WorkerRecipePlan.Operation.PROCESSING, inputs, item(result), result.getCount());
    }

    public static List<WorkerRecipePlan> stages(Level level, WorkerRecipePlan plan, SequencedAssemblyRecipe recipe){
        if(plan.stage() >= 0) return List.of(plan);
        Map<WorkerResourceKey, Long> stock = new LinkedHashMap<>();
        plan.inputs().forEach(input -> stock.merge(input.resource(), input.amount(), Long::sum));
        WorkerResourceKey base = choose(recipe.getIngredient(), stock);
        if(base == null) return List.of();
        List<WorkerRecipePlan> stages = new ArrayList<>();
        int count = recipe.getSequence().size() * recipe.getLoops();
        for(int idx = 0; idx < count; idx++){
            var processing = recipe.getSequence().get(idx % recipe.getSequence().size()).getRecipe();
            List<WorkerRecipePlan.Input> inputs = new ArrayList<>();
            for(int inputIdx = 1; inputIdx < processing.getIngredients().size(); inputIdx++){
                WorkerResourceKey selected = choose(processing.getIngredients().get(inputIdx), stock);
                if(selected == null) return List.of();
                inputs.add(new WorkerRecipePlan.Input(selected, 1L));
            }
            for(var fluid : processing.getFluidIngredients()){
                WorkerResourceKey selected = stock.keySet().stream().filter(key -> key.type() == WorkerResourceType.FLUID
                                && stock.get(key) >= fluid.amount() && Arrays.stream(fluid.getFluids())
                                .anyMatch(stack -> BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(key.id())))
                        .findFirst().orElse(null);
                if(selected == null) return List.of();
                stock.computeIfPresent(selected, (key, amount) -> amount - fluid.amount());
                inputs.add(new WorkerRecipePlan.Input(selected, fluid.amount()));
            }
            // Supply tools and fluid before the workpiece can move past a belt-mounted machine
            inputs.add(new WorkerRecipePlan.Input(idx == 0 ? base : item(recipe.getTransitionalItem()), 1L));
            boolean last = idx == count - 1;
            stages.add(new WorkerRecipePlan(plan.recipeId(), BuiltInRegistries.RECIPE_TYPE.getKey(processing.getType()),
                    WorkerRecipePlan.Operation.PROCESSING, inputs, last ? plan.result() : item(recipe.getTransitionalItem()),
                    last ? plan.resultAmount() : 1L, idx));
        }
        return List.copyOf(stages);
    }

    public static Recipe<?> recipe(WorkerRecipePlan plan, SequencedAssemblyRecipe recipe){
        return plan.stage() < 0 ? recipe : recipe.getSequence().get(plan.stage() % recipe.getSequence().size()).getRecipe();
    }

    public static boolean matchesInput(WorkerRecipePlan plan, SequencedAssemblyRecipe recipe, ItemStack stack){
        if(plan.stage() < 0 || !stack.is(recipe.getTransitionalItem().getItem())) return true;
        var progress = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        return progress != null && progress.id().equals(plan.recipeId()) && progress.step() == plan.stage();
    }

    public static boolean matchesOutput(WorkerRecipePlan plan, SequencedAssemblyRecipe recipe, ItemStack stack){
        if(plan.stage() < 0 || plan.stage() + 1 == recipe.getSequence().size() * recipe.getLoops()) return true;
        var progress = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        return progress != null && progress.id().equals(plan.recipeId()) && progress.step() == plan.stage() + 1;
    }

    // Return a completed pass to the first station without repeating its supplied tools or fluids
    public static boolean recirculates(WorkerRecipePlan plan, SequencedAssemblyRecipe recipe, ItemStack stack){
        if(plan.stage() >= 0 || recipe.getSequence().isEmpty() || !stack.is(recipe.getTransitionalItem().getItem())) return false;
        var progress = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        return progress != null && progress.id().equals(plan.recipeId()) && progress.step() > 0
                && progress.step() < recipe.getLoops() * recipe.getSequence().size()
                && progress.step() % recipe.getSequence().size() == 0;
    }

    private static WorkerRecipeDefinition.Ingredient ingredient(Ingredient ingredient, long amount){
        return new WorkerRecipeDefinition.Ingredient(Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty()).map(CreateWorkerSequences::item).toList(), amount);
    }

    private static WorkerResourceKey item(ItemStack stack){
        return new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static WorkerResourceKey choose(Ingredient ingredient, Map<WorkerResourceKey, Long> stock){
        for(var entry : stock.entrySet()){
            if(entry.getKey().type() != WorkerResourceType.ITEM || entry.getValue() <= 0L) continue;
            if(!ingredient.test(new ItemStack(BuiltInRegistries.ITEM.get(entry.getKey().id())))) continue;
            entry.setValue(entry.getValue() - 1L);
            return entry.getKey();
        }
        return null;
    }
}
