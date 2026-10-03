package com.rieno.gadgetsandgizmos.lib.worker;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Describe live single-item processing recipes for machine and recipe viewer integrations
public final class WorkerProcessingRecipeViews{
    private WorkerProcessingRecipeViews(){}

    public record Output(ItemStack stack, float chance){
        public Output{
            stack = stack.copy();
        }
    }

    public record View(RecipeHolder<?> source, Ingredient input, List<Output> outputs){
        public View{
            outputs = List.copyOf(outputs);
        }
    }

    // Read current datapack recipes rather than retaining a stale recipe list
    public static List<View> recipes(Level level, Collection<ResourceLocation> types){
        if(level == null || types == null || types.isEmpty()) return List.of();
        List<View> views = new ArrayList<>();
        for(RecipeHolder<?> holder : level.getRecipeManager().getRecipes()){
            ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
            if(!types.contains(type) || holder.id().getPath().endsWith("_manual_only")
                    || holder.value().getIngredients().size() != 1) continue;
            Ingredient input = holder.value().getIngredients().getFirst();
            if(input.isEmpty() || input.getItems().length == 0) continue;
            List<Output> outputs = new ArrayList<>();
            if(holder.value() instanceof ProcessingRecipe<?, ?> processing){
                if(!processing.getFluidIngredients().isEmpty()) continue;
                for(var result : processing.getRollableResults()){
                    if(!result.getStack().isEmpty() && result.getChance() > 0.0F){
                        outputs.add(new Output(result.getStack(), result.getChance()));
                    }
                }
            }else{
                ItemStack result = holder.value().getResultItem(level.registryAccess());
                if(!result.isEmpty()) outputs.add(new Output(result, 1.0F));
            }
            if(!outputs.isEmpty()) views.add(new View(holder, input, outputs));
        }
        return List.copyOf(views);
    }
}
