package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkerRecipeDisplayTest{
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            LoadingModList mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    @AfterEach void clearCache(){ WorkerRecipeCatalog.invalidate(); }

    // Alternate machine routes keep one saved id and the actual recipe output
    @Test void displaysResultComponentsWithoutReplacingRecipeIdentity(){
        Level level = mock(Level.class);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(recipe).getType();
        when(recipe.canCraftInDimensions(2, 2)).thenReturn(true);
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_LOG)));
        ItemStack output = new ItemStack(Items.OAK_PLANKS, 4);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("Varnished Oak"));
        when(recipe.getResultItem(any())).thenReturn(output);
        ResourceLocation id = ResourceLocation.parse("test:processed_wood");
        RecipeHolder<CraftingRecipe> holder = new RecipeHolder<>(id, recipe);
        when(manager.getRecipes()).thenReturn(List.of(holder));
        when(manager.byKey(id)).thenReturn(Optional.of(holder));

        var entries = WorkerRecipeDisplay.entries(level);
        var matches = entries.stream().filter(entry -> entry.recipeId().equals(id)).toList();
        assertEquals(1, matches.size());
        var entry = matches.getFirst();
        assertEquals(output.getHoverName(), entry.name());
        assertTrue(entry.icon().is(Items.OAK_PLANKS));
        assertEquals(output.get(DataComponents.CUSTOM_NAME), entry.icon().get(DataComponents.CUSTOM_NAME));
        assertNotSame(output, entry.icon());
        assertTrue(entry.matches("varnished oak"));
        assertTrue(entry.matches("test:processed"));
        assertEquals(id, entry.recipeId());
    }

    // World recipes have no recipe manager holder or item matching their recipe id
    @Test void displaysSyntheticWorldRecipeFromItsOutputResource(){
        Level level = mock(Level.class);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.getRecipes()).thenReturn(List.of());
        ResourceLocation id = ResourceLocation.parse("createthrusters:world/axe_strip/minecraft/oak_log");

        var entry = WorkerRecipeDisplay.entries(level).stream()
                .filter(candidate -> candidate.recipeId().equals(id)).findFirst().orElseThrow();
        assertEquals(new ItemStack(Items.STRIPPED_OAK_LOG).getHoverName(), entry.name());
        assertTrue(entry.icon().is(Items.STRIPPED_OAK_LOG));
        assertEquals(id, entry.recipeId());
    }
}
