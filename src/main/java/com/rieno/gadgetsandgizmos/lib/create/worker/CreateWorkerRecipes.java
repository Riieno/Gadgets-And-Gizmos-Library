package com.rieno.gadgetsandgizmos.lib.create.worker;

import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

// Match legacy single-item work against each Create recipe's declared input contract
public final class CreateWorkerRecipes{
    private CreateWorkerRecipes(){}

    // Return the selected result count without rolling outputs or modifying the shared recipe
    public static int singleItemOutput(ProcessingRecipe<?, ?> recipe, Level level, ItemStack input, Item output){
        if(input.isEmpty() || recipe.getIngredients().size() != 1 || !recipe.getFluidIngredients().isEmpty()) return 0;
        ItemStack result = recipe.getRollableResultsAsItemStacks().stream()
                .filter(stack -> stack.is(output)).findFirst().orElse(ItemStack.EMPTY);
        if(result.isEmpty() || !matchesSingleItem(recipe, level, input)) return 0;
        return result.getCount();
    }

    // Unknown and multi-input recipes require a complete plan instead of an erased Recipe cast
    private static boolean matchesSingleItem(ProcessingRecipe<?, ?> recipe, Level level, ItemStack input){
        SingleRecipeInput single = new SingleRecipeInput(input.copy());
        return switch(recipe){
            case CuttingRecipe cutting -> {
                ItemStackHandler inventory = new ItemStackHandler(1);
                inventory.setStackInSlot(0, input.copy());
                yield cutting.matches(new RecipeWrapper(inventory), level);
            }
            case AbstractCrushingRecipe crushing -> crushing.matches(single, level);
            case PressingRecipe pressing -> pressing.matches(single, level);
            case HauntingRecipe haunting -> haunting.matches(single, level);
            case SplashingRecipe splashing -> splashing.matches(single, level);
            case SandPaperPolishingRecipe polishing -> polishing.matches(single, level);
            case EmptyingRecipe emptying -> emptying.matches(single, level);
            case FillingRecipe filling -> filling.matches(single, level);
            default -> false;
        };
    }
}
