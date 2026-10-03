package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Recover the recipe's actual slot layout from its reserved ingredient counts
public final class WorkerCraftingGrid{
    private WorkerCraftingGrid(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Assemble through the recipe itself, retaining container remainders such as empty buckets
    public static Result craft(Level level, WorkerRecipePlan plan){
        return craft(level, plan, Map.of());
    }

    // Use the actual equipped tool stack so recipes can apply durability or components to its remainder.
    public static Result craft(Level level, WorkerRecipePlan plan, Map<WorkerResourceKey, ItemStack> tools){
        Result portable = WorkerRecipeCatalog.craftWithAdapter(level, plan, tools);
        if(!portable.output().isEmpty()) return portable;
        Recipe<?> recipe = WorkerRecipeCatalog.recipe(level, plan);
        if(recipe instanceof CraftingRecipe crafting){
            CraftingInput input = create(recipe, plan, tools);
            int size = plan.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING ? 2 : 3;
            if(input.width() > size || input.height() > size || !crafting.matches(input, level)) return Result.EMPTY;
            return new Result(crafting.assemble(input, level.registryAccess()), List.copyOf(crafting.getRemainingItems(input)));
        }
        if(recipe instanceof StonecutterRecipe cutting && plan.inputs().size() == 1){
            var input = new SingleRecipeInput(new ItemStack(BuiltInRegistries.ITEM.get(plan.inputs().getFirst().resource().id())));
            return cutting.matches(input, level) ? new Result(cutting.assemble(input, level.registryAccess()), List.of()) : Result.EMPTY;
        }
        return Result.EMPTY;
    }

    public record Result(ItemStack output, List<ItemStack> remainders){
        public static final Result EMPTY = new Result(ItemStack.EMPTY, List.of());
    }

    public static CraftingInput create(Recipe<?> recipe, WorkerRecipePlan plan){
        return create(recipe, plan, Map.of());
    }

    public static CraftingInput create(Recipe<?> recipe, WorkerRecipePlan plan,
                                       Map<WorkerResourceKey, ItemStack> tools){
        List<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth()
                : Math.min(ingredients.size() <= 4 ? 2 : 3, Math.max(1, ingredients.size()));
        int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight()
                : Math.max(1, (ingredients.size() + width - 1) / width);
        List<ItemStack> slots = new ArrayList<>(java.util.Collections.nCopies(width * height, ItemStack.EMPTY));
        Map<WorkerResourceKey, Long> stock = new LinkedHashMap<>();
        for(var input : plan.inputs()){
            if(input.resource().type() != WorkerResourceType.ITEM) return CraftingInput.EMPTY;
            stock.merge(input.resource(), input.amount(), Long::sum);
        }
        List<Integer> occupied = new ArrayList<>();
        for(int slot = 0; slot < ingredients.size(); slot++)
            if(ingredients.get(slot) != Ingredient.EMPTY) occupied.add(slot);
        if(stock.values().stream().mapToLong(Long::longValue).sum() != occupied.size())
            return CraftingInput.EMPTY;
        List<WorkerResourceKey> resources = new ArrayList<>(stock.keySet());
        int resourceCount = resources.size();
        int ingredientCount = occupied.size();
        int source = 0;
        int firstIngredient = resourceCount + 1;
        int sink = firstIngredient + ingredientCount;
        int[][] capacity = new int[sink + 1][sink + 1];
        ItemStack[] samples = new ItemStack[resourceCount];
        for(int resource = 0; resource < resourceCount; resource++){
            WorkerResourceKey key = resources.get(resource);
            samples[resource] = tools.getOrDefault(key,
                    new ItemStack(BuiltInRegistries.ITEM.get(key.id()))).copyWithCount(1);
            capacity[source][resource + 1] = (int)Math.min(ingredientCount, stock.get(key));
            for(int ingredient = 0; ingredient < ingredientCount; ingredient++)
                if(!samples[resource].isEmpty()
                        && ingredients.get(occupied.get(ingredient)).test(samples[resource]))
                    capacity[resource + 1][firstIngredient + ingredient] = 1;
        }
        for(int ingredient = 0; ingredient < ingredientCount; ingredient++)
            capacity[firstIngredient + ingredient][sink] = 1;
        for(int matched = 0; matched < ingredientCount; matched++){
            int[] previous = new int[sink + 1];
            Arrays.fill(previous, -1);
            previous[source] = source;
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            pending.add(source);
            while(!pending.isEmpty() && previous[sink] < 0){
                int node = pending.removeFirst();
                for(int next = 0; next <= sink; next++){
                    if(capacity[node][next] <= 0 || previous[next] >= 0) continue;
                    previous[next] = node;
                    pending.addLast(next);
                }
            }
            if(previous[sink] < 0) return CraftingInput.EMPTY;
            for(int node = sink; node != source; node = previous[node]){
                capacity[previous[node]][node]--;
                capacity[node][previous[node]]++;
            }
        }
        for(int ingredient = 0; ingredient < ingredientCount; ingredient++){
            for(int resource = 0; resource < resourceCount; resource++){
                if(capacity[firstIngredient + ingredient][resource + 1] <= 0) continue;
                slots.set(occupied.get(ingredient), samples[resource].copy());
                break;
            }
        }
        return CraftingInput.of(width, height, slots);
    }
}
