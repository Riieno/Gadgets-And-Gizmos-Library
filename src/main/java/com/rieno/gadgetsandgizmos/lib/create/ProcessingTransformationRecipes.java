package com.rieno.gadgetsandgizmos.lib.create;

import com.rieno.gadgetsandgizmos.lib.item.TimedTransformingItem;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// Describe a real Create recipe followed by an item's timed transformation.
public final class ProcessingTransformationRecipes{
    private ProcessingTransformationRecipes(){}

    public record View(ResourceLocation id, RecipeHolder<?> source, List<Ingredient> itemInputs,
                       List<List<FluidStack>> fluidInputs, ItemStack intermediate, ItemStack result,
                       Component process, Component completion){}

    public static Optional<View> create(ResourceLocation id, RecipeHolder<?> source,
                                        Component process, Component completion){
        if(!(source.value() instanceof ProcessingRecipe<?, ?> recipe)) return Optional.empty();
        if(recipe.getRollableResults().size() != 1 || !recipe.getFluidResults().isEmpty()) return Optional.empty();
        var output = recipe.getRollableResults().getFirst();
        if(output.getChance() != 1.0F || !(output.getStack().getItem() instanceof TimedTransformingItem item)){
            return Optional.empty();
        }
        if(recipe.getIngredients().size() + recipe.getFluidIngredients().size() > 12) return Optional.empty();
        ItemStack intermediate = output.getStack().copy();
        List<List<FluidStack>> fluids = recipe.getFluidIngredients().stream()
                .map(ingredient -> Arrays.stream(ingredient.getFluids()).map(FluidStack::copy).toList()).toList();
        return Optional.of(new View(id, source, List.copyOf(recipe.getIngredients()), fluids, intermediate,
                item.completeTransformation(intermediate), process, completion));
    }
}
