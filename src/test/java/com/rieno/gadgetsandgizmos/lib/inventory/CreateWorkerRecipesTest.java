package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.create.worker.CreateWorkerRecipes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateWorkerRecipesTest{
    @BeforeAll static void bootstrap(){ InventoryTestBootstrap.bootstrap(); }

    // Exercise Create's real bridge method and matcher without depending on registered serializers
    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void cuttingUsesAnInventoryWrapperInsteadOfCastingASingleInput() throws Exception{
        var recipe = recipe(CuttingRecipe.class);
        Level level = mock(Level.class);
        ItemStack input = new ItemStack(Items.IRON_INGOT, 12);
        assertThrows(ClassCastException.class, () -> ((Recipe)recipe).matches(new SingleRecipeInput(input), level));
        assertEquals(2, CreateWorkerRecipes.singleItemOutput(recipe, level, input, Items.IRON_NUGGET));
        assertEquals(12, input.getCount());
        assertEquals(0, CreateWorkerRecipes.singleItemOutput(recipe, level, new ItemStack(Items.OAK_LOG), Items.IRON_NUGGET));
        assertEquals(0, CreateWorkerRecipes.singleItemOutput(recipe, level, input, Items.DIAMOND));
    }

    @Test void pressingUsesTheSingleItemContractWithoutForcingRecipeResults() throws Exception{
        var recipe = recipe(PressingRecipe.class);
        assertEquals(2, CreateWorkerRecipes.singleItemOutput(recipe, mock(Level.class),
                new ItemStack(Items.IRON_INGOT), Items.IRON_NUGGET));
        verify(recipe, never()).enforceNextResult(any());
    }

    @Test void aFluidRecipeCannotBeMatchedByItsItemAlone() throws Exception{
        var recipe = recipe(FillingRecipe.class);
        set(recipe, "fluidIngredients", NonNullList.of(null, SizedFluidIngredient.of(Fluids.WATER, 100)));
        assertEquals(0, CreateWorkerRecipes.singleItemOutput(recipe, mock(Level.class),
                new ItemStack(Items.IRON_INGOT), Items.IRON_NUGGET));
        verify(recipe, never()).matches(any(), any());
    }

    private static <T extends ProcessingRecipe<?, ?>> T recipe(Class<T> type) throws Exception{
        T recipe = mock(type, CALLS_REAL_METHODS);
        set(recipe, "ingredients", NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT)));
        set(recipe, "results", NonNullList.of(ProcessingOutput.EMPTY, new ProcessingOutput(new ItemStack(Items.IRON_NUGGET, 2), 1F)));
        set(recipe, "fluidIngredients", NonNullList.create());
        return recipe;
    }

    private static void set(Object recipe, String name, Object val) throws Exception{
        var field = ProcessingRecipe.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(recipe, val);
    }
}
