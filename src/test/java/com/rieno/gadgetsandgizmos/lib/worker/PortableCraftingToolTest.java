package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PortableCraftingToolTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)) {
            LoadingModList mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    @Test void craftsThreeByThreeWithOneReusableEquippedTool() {
        ResourceLocation toolId = BuiltInRegistries.ITEM.getKey(Items.CRAFTING_TABLE);
        WorkerRecipeCatalog.registerPortableCraftingTool(toolId);
        Level level = mock(Level.class);
        RecipeManager manager = mock(RecipeManager.class);
        RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(access);
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(recipe).getType();
        when(recipe.canCraftInDimensions(2, 2)).thenReturn(false);
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT),
                Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT),
                Ingredient.of(Items.IRON_INGOT)));
        when(recipe.getResultItem(access)).thenReturn(new ItemStack(Items.DIAMOND));
        when(recipe.matches(any(CraftingInput.class), eq(level))).thenReturn(true);
        when(recipe.assemble(any(CraftingInput.class), eq(access)))
                .thenReturn(new ItemStack(Items.DIAMOND));
        when(recipe.getRemainingItems(any(CraftingInput.class))).thenReturn(NonNullList.create());
        ResourceLocation recipeId = ResourceLocation.parse("test:large_portable_craft");
        RecipeHolder<?> holder = new RecipeHolder<>(recipeId, recipe);
        doReturn(List.of(holder)).when(manager).getRecipes();
        doReturn(java.util.Optional.of(holder)).when(manager).byKey(recipeId);

        WorkerRecipeDefinition portable = WorkerRecipeCatalog.index(level).definitions().stream()
                .filter(def -> def.recipeId().equals(recipeId)
                        && def.processorType().equals(toolId)).findFirst().orElseThrow();
        WorkerResourceKey ingredient = new WorkerResourceKey(WorkerResourceType.ITEM,
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT));
        WorkerResourceKey tool = new WorkerResourceKey(WorkerResourceType.ITEM, toolId);
        var planned = WorkerRecipePlanner.planDetailed(portable.result(), 4,
                Map.of(ingredient, 20L, tool, 1L),
                new WorkerRecipeIndex(List.of(portable)), null,
                def -> true, plan -> true, plan -> true, plan -> 0,
                (def, index) -> WorkerRecipeCatalog.reusableToolCredit(def, index, Map.of(tool, 1L)));
        assertTrue(planned.chain().executable(), planned.failure().message());
        WorkerRecipePlan plan = planned.chain().steps().getFirst().plan();
        var crafted = WorkerCraftingGrid.craft(level, plan, Map.of(tool, new ItemStack(Items.CRAFTING_TABLE)));
        assertTrue(crafted.output().is(Items.DIAMOND));
        assertTrue(crafted.remainders().stream().anyMatch(stack -> stack.is(Items.CRAFTING_TABLE)));
    }

    @Test void matchesNineOverlappingIngredientsWithoutRecursiveSearch() {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        var alternatives = Ingredient.of(Items.IRON_INGOT, Items.STICK, Items.COPPER_INGOT,
                Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD, Items.REDSTONE,
                Items.LAPIS_LAZULI, Items.COAL);
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                alternatives, alternatives, alternatives, alternatives, alternatives,
                alternatives, alternatives, alternatives, alternatives));
        List<WorkerRecipePlan.Input> inputs = List.of(Items.IRON_INGOT, Items.STICK,
                Items.COPPER_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD,
                Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL).stream()
                .map(item -> new WorkerRecipePlan.Input(new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(item)), 1L)).toList();
        WorkerRecipePlan plan = new WorkerRecipePlan(ResourceLocation.parse("test:overlapping_grid"),
                ResourceLocation.parse("minecraft:crafting"), WorkerRecipePlan.Operation.CRAFTING,
                inputs, new WorkerResourceKey(WorkerResourceType.ITEM,
                BuiltInRegistries.ITEM.getKey(Items.NETHER_STAR)), 1L);
        CraftingInput grid = WorkerCraftingGrid.create(recipe, plan);
        assertEquals(9, grid.items().stream().filter(stack -> !stack.isEmpty()).count());
        assertEquals(9, grid.items().stream().map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .distinct().count());
    }
}
