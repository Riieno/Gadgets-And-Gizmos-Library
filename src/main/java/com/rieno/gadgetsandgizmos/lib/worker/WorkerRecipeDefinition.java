package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

// Retain ingredient alternatives until a complete dependency tree can be reserved
public record WorkerRecipeDefinition(ResourceLocation recipeId, ResourceLocation processorType,
                                     WorkerRecipePlan.Operation operation, List<Ingredient> ingredients,
                                     WorkerResourceKey result, long resultAmount){
    // Copy the recipe's ordered ingredient slots
    public WorkerRecipeDefinition{
        ingredients = List.copyOf(ingredients);
        if(resultAmount <= 0L) throw new IllegalArgumentException("Recipe output must be positive");
    }

    // Describe the interchangeable resources accepted by one ingredient slot
    public record Ingredient(List<WorkerResourceKey> alternatives, long amount){
        // Keep distinct alternatives without changing the supplied preference order
        public Ingredient{
            alternatives = alternatives.stream().distinct().toList();
            if(amount <= 0L) throw new IllegalArgumentException("Ingredient amount must be positive");
        }
    }
}
