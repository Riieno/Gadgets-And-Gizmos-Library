package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkerRecipeIndexTest{
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

    // Resolve tag members once and reuse them until a datapack reload invalidates the graph
    @Test void cachesExpandedAlternativesUntilInvalidated(){
        Level level = mock(Level.class);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(recipe).getType();
        when(recipe.canCraftInDimensions(2, 2)).thenReturn(true);
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS)));
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.CRAFTING_TABLE));
        CraftingRecipe unrelated = mock(CraftingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(unrelated).getType();
        when(unrelated.canCraftInDimensions(2, 2)).thenReturn(true);
        when(unrelated.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.IRON_INGOT)));
        when(unrelated.getResultItem(any())).thenReturn(new ItemStack(Items.IRON_BLOCK));
        when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(
                ResourceLocation.parse("test:tagged_table"), recipe),
                new RecipeHolder<>(ResourceLocation.parse("test:unrelated_block"), unrelated)));

        WorkerRecipeIndex index = WorkerRecipeCatalog.index(level);
        assertSame(index, WorkerRecipeCatalog.index(level));
        verify(manager, times(1)).getRecipes();
        WorkerRecipeDefinition def = index.producing(new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.parse("minecraft:crafting_table"))).getFirst();
        assertEquals(List.of(ResourceLocation.parse("minecraft:oak_planks"),
                        ResourceLocation.parse("minecraft:birch_planks")),
                def.ingredients().getFirst().alternatives().stream().map(WorkerResourceKey::id).toList());
        assertEquals(List.of(def), index.consuming(new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.parse("minecraft:birch_planks"))));
        assertTrue(index.relevantResources(def.result()).contains(new WorkerResourceKey(
                WorkerResourceType.ITEM, ResourceLocation.parse("minecraft:birch_planks"))));
        assertFalse(index.relevantResources(def.result()).contains(new WorkerResourceKey(
                WorkerResourceType.ITEM, ResourceLocation.parse("minecraft:iron_ingot"))));
        assertTrue(WorkerRecipePlanner.planDetailed(def.result(), 1,
                Map.of(new WorkerResourceKey(WorkerResourceType.ITEM,
                        ResourceLocation.parse("minecraft:birch_planks")), 1L), index, null,
                candidate -> {
                    assertEquals(def.result(), candidate.result());
                    return true;
                }, candidate -> true, null).chain().executable());

        WorkerRecipeCatalog.invalidate();
        assertNotSame(index, WorkerRecipeCatalog.index(level));
        verify(manager, times(2)).getRecipes();
    }

    @Test void indexesAxeStrippingAsAWorldPrerequisite(){
        Level level = mock(Level.class);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.getRecipes()).thenReturn(List.of());
        WorkerResourceKey stripped = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("stripped_oak_log"));
        WorkerRecipeDefinition recipe = WorkerRecipeCatalog.index(level).producing(stripped).stream()
                .filter(def -> WorkerRecipeCatalog.WORLD_AXE_STRIP.equals(def.processorType()))
                .findFirst().orElseThrow();
        assertEquals(ResourceLocation.withDefaultNamespace("oak_log"),
                recipe.ingredients().getFirst().alternatives().getFirst().id());
        assertTrue(recipe.ingredients().get(1).alternatives().stream()
                .anyMatch(key -> key.id().equals(ResourceLocation.withDefaultNamespace("iron_axe"))));
    }

    @Test void itemApplicationExposesDeployerAndWorldRoutes() throws Exception{
        Level level = mock(Level.class);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        ManualApplicationRecipe recipe = mock(ManualApplicationRecipe.class);
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.STRIPPED_OAK_LOG), Ingredient.of(Items.IRON_INGOT)));
        when(recipe.getFluidIngredients()).thenReturn(NonNullList.create());
        when(recipe.getFluidResults()).thenReturn(NonNullList.create());
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.IRON_BLOCK));
        var method = WorkerRecipeCatalog.class.getDeclaredMethod("standard", Level.class,
                RecipeHolder.class, ResourceLocation.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<WorkerRecipeDefinition> routes = (List<WorkerRecipeDefinition>)method.invoke(null, level,
                new RecipeHolder<>(ResourceLocation.parse("test:application"), recipe),
                ResourceLocation.parse("create:item_application"));
        assertEquals(2, routes.size());
        assertEquals(ResourceLocation.parse("create:deploying"), routes.getFirst().processorType());
        assertEquals(ResourceLocation.withDefaultNamespace("iron_ingot"),
                routes.getFirst().ingredients().getFirst().alternatives().getFirst().id());
        assertEquals(WorkerRecipeCatalog.WORLD_ITEM_APPLICATION, routes.get(1).processorType());
        assertEquals(ResourceLocation.withDefaultNamespace("stripped_oak_log"),
                routes.get(1).ingredients().getFirst().alternatives().getFirst().id());
        var oakLog = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("oak_log"));
        var strippedLog = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("stripped_oak_log"));
        var iron = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("iron_ingot"));
        var axe = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("iron_axe"));
        var output = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("iron_block"));
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.getRecipes()).thenReturn(List.of());
        WorkerRecipeDefinition strip = WorkerRecipeCatalog.index(level).producing(strippedLog).stream()
                .filter(def -> WorkerRecipeCatalog.WORLD_AXE_STRIP.equals(def.processorType()))
                .findFirst().orElseThrow();
        var chain = WorkerRecipePlanner.planDetailed(output, 1,
                Map.of(oakLog, 1L, iron, 1L, axe, 1L), List.of(strip, routes.getFirst(), routes.get(1)),
                null, plan -> plan.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING).chain();
        assertTrue(chain.executable());
        assertEquals(List.of(WorkerRecipeCatalog.WORLD_AXE_STRIP,
                        WorkerRecipeCatalog.WORLD_ITEM_APPLICATION),
                chain.steps().stream().map(step -> step.plan().processorType()).toList());
        var preferred = WorkerRecipePlanner.planDetailed(output, 1,
                Map.of(oakLog, 1L, iron, 1L, axe, 1L),
                new WorkerRecipeIndex(List.of(strip, routes.getFirst(), routes.get(1))),
                null, candidate -> true, plan -> true, null,
                plan -> WorkerRecipeCatalog.WORLD_ITEM_APPLICATION.equals(plan.processorType()) ? 8 : 0);
        assertEquals(ResourceLocation.parse("create:deploying"),
                preferred.chain().steps().getLast().plan().processorType());
    }

    @Test void resolvesOpaqueIngredientByItsActualItemPredicate() throws Exception{
        Level level = mock(Level.class);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Ingredient opaque = mock(Ingredient.class);
        when(opaque.isEmpty()).thenReturn(true);
        when(opaque.getItems()).thenReturn(new ItemStack[0]);
        when(opaque.test(any(ItemStack.class))).thenAnswer(call ->
                ((ItemStack)call.getArgument(0)).is(Items.FLINT));
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        when(recipe.canCraftInDimensions(2, 2)).thenReturn(true);
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, opaque));
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.CRAFTING_TABLE));
        var method = WorkerRecipeCatalog.class.getDeclaredMethod("standard", Level.class,
                RecipeHolder.class, ResourceLocation.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<WorkerRecipeDefinition> routes = (List<WorkerRecipeDefinition>)method.invoke(null, level,
                new RecipeHolder<>(ResourceLocation.parse("test:opaque"), recipe),
                ResourceLocation.withDefaultNamespace("crafting"));
        assertEquals(List.of(ResourceLocation.withDefaultNamespace("flint")), routes.getFirst()
                .ingredients().getFirst().alternatives().stream().map(WorkerResourceKey::id).toList());
    }
}
