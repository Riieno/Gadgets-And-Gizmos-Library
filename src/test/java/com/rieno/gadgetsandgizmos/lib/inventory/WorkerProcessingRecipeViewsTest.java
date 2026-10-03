package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerProcessingRecipeViews;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkerProcessingRecipeViewsTest{
    @BeforeAll static void bootstrap(){ InventoryTestBootstrap.bootstrap(); }

    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void readsLiveRecipesAndPreservesAlternateOutputs(){
        Level level = mock(Level.class);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        SmeltingRecipe smelting = mock(SmeltingRecipe.class);
        doReturn(RecipeType.SMELTING).when(smelting).getType();
        when(smelting.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.RAW_IRON)));
        when(smelting.getResultItem(any())).thenReturn(new ItemStack(Items.IRON_INGOT));
        ProcessingRecipe processing = mock(ProcessingRecipe.class);
        when(processing.getType()).thenReturn(RecipeType.SMOKING);
        when(processing.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_LOG)));
        when(processing.getFluidIngredients()).thenReturn(NonNullList.create());
        when(processing.getRollableResults()).thenReturn(List.of(
                new ProcessingOutput(new ItemStack(Items.CHARCOAL), 1.0F),
                new ProcessingOutput(new ItemStack(Items.STICK), 0.25F)));
        when(manager.getRecipes()).thenReturn(List.of(
                new RecipeHolder<>(ResourceLocation.parse("test:iron"), smelting),
                new RecipeHolder<>(ResourceLocation.parse("test:charcoal"), processing),
                new RecipeHolder<>(ResourceLocation.parse("test:hidden_manual_only"), smelting)));

        var views = WorkerProcessingRecipeViews.recipes(level, List.of(
                ResourceLocation.parse("minecraft:smelting"), ResourceLocation.parse("minecraft:smoking")));
        assertEquals(2, views.size());
        assertEquals(ResourceLocation.parse("test:iron"), views.getFirst().source().id());
        assertEquals(2, views.getLast().outputs().size());
        assertEquals(0.25F, views.getLast().outputs().get(1).chance());
    }
}
