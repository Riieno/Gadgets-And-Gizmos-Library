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
import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;
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

    // Probe opaque ingredients on the owner thread across ticks and discard indexes after a reload
    @Test void defersOpaqueProbesAndSharesTheResolvedIndex(){
        Level level = mock(Level.class);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getServer()).thenReturn(mock(net.minecraft.server.MinecraftServer.class));
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Ingredient opaque = mock(Ingredient.class);
        when(opaque.getItems()).thenReturn(new ItemStack[0]);
        Thread owner = Thread.currentThread();
        java.util.concurrent.atomic.AtomicInteger probes = new java.util.concurrent.atomic.AtomicInteger();
        when(opaque.test(any())).thenAnswer(call -> {
            assertSame(owner, Thread.currentThread());
            probes.incrementAndGet();
            return ((ItemStack)call.getArgument(0)).is(Items.FLINT);
        });
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(recipe).getType();
        when(recipe.canCraftInDimensions(2, 2)).thenReturn(true);
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, opaque));
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.CRAFTING_TABLE));
        var holder = new RecipeHolder<>(ResourceLocation.parse("test:deferred"), recipe);
        when(manager.getRecipes()).thenReturn(List.of(holder));
        try(var scheduler = DeferredWorkScheduler.forServer(level.getServer())){
            var first = WorkerRecipeCatalog.prepareIndex(level);
            assertSame(first, WorkerRecipeCatalog.prepareIndex(level));
            assertFalse(first.isDone());
            scheduler.tick();
            assertFalse(first.isDone());
            drain(scheduler, first);
            WorkerRecipeIndex index = first.join();
            var output = new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace("crafting_table"));
            assertSame(index, WorkerRecipeCatalog.deferredIndex(level));
            assertEquals(List.of(holder), WorkerRecipeCatalog.producingRecipes(level, output));
            assertEquals(List.of(ResourceLocation.withDefaultNamespace("flint")), index.producing(output).getFirst()
                    .ingredients().getFirst().alternatives().stream().map(WorkerResourceKey::id).toList());
            assertTrue(probes.get() > 32);
            verify(manager, times(1)).getRecipes();
            WorkerRecipeCatalog.invalidate();
            var cancelled = WorkerRecipeCatalog.prepareIndex(level);
            WorkerRecipeCatalog.invalidate();
            assertTrue(cancelled.isCancelled());
            var rebuilt = WorkerRecipeCatalog.prepareIndex(level);
            drain(scheduler, rebuilt);
            assertNotSame(index, rebuilt.join());
            assertSame(rebuilt.join(), WorkerRecipeCatalog.deferredIndex(level));
        }
    }

    // A simple craft must not probe thousands of unrelated recipes before it can run
    @Test void resolvesRequestedDependenciesWithoutTouchingUnrelatedIngredients(){
        Level level = mock(Level.class);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getServer()).thenReturn(mock(net.minecraft.server.MinecraftServer.class));
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        CraftingRecipe planks = mock(CraftingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(planks).getType();
        when(planks.canCraftInDimensions(2, 2)).thenReturn(true);
        when(planks.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_LOG)));
        when(planks.getResultItem(any())).thenReturn(new ItemStack(Items.OAK_PLANKS, 4));
        CraftingRecipe table = mock(CraftingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(table).getType();
        when(table.canCraftInDimensions(2, 2)).thenReturn(true);
        when(table.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_PLANKS)));
        when(table.getResultItem(any())).thenReturn(new ItemStack(Items.CRAFTING_TABLE));
        Ingredient opaque = mock(Ingredient.class);
        CraftingRecipe unrelated = mock(CraftingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(unrelated).getType();
        when(unrelated.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, opaque));
        when(unrelated.getResultItem(any())).thenReturn(new ItemStack(Items.IRON_BLOCK));
        List<RecipeHolder<?>> holders = new java.util.ArrayList<>();
        holders.add(new RecipeHolder<>(ResourceLocation.parse("test:planks"), planks));
        holders.add(new RecipeHolder<>(ResourceLocation.parse("test:table"), table));
        for(int idx = 0; idx < 10_000; idx++)
            holders.add(new RecipeHolder<>(ResourceLocation.parse("test:unrelated_" + idx), unrelated));
        when(manager.getRecipes()).thenReturn(holders);
        var output = new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace("crafting_table"));
        var plank = new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace("oak_planks"));
        try(var scheduler = DeferredWorkScheduler.forServer(level.getServer())){
            var prepared = WorkerRecipeCatalog.prepareLookup(level);
            drain(scheduler, prepared, 15_000_000_000L);
            WorkerRecipeSource source = WorkerRecipeCatalog.deferredSource(level);
            var stocked = scheduler.submit(() -> WorkerRecipePlanner.planFromStock(output, 1L,
                    Map.of(plank, 1L), source, null, def -> true, plan -> true, null, plan -> 0, (def, idx) -> 0L));
            drain(scheduler, stocked);
            assertTrue(stocked.join().chain().executable());
            verify(planks, never()).getIngredients();
            clearInvocations(table);
            var another = scheduler.submit(() -> source.producing(output));
            drain(scheduler, another);
            assertEquals(1, another.join().size());
            verify(table, never()).getIngredients();
            assertSame(source, WorkerRecipeCatalog.deferredSource(level));
            WorkerRecipeIndex index = lookup(scheduler, level, output);
            assertEquals(List.of(ResourceLocation.parse("test:table"), ResourceLocation.parse("test:planks")),
                    index.dependencies(output).stream().map(WorkerRecipeDefinition::recipeId).toList());
            assertSame(index, WorkerRecipeCatalog.deferredIndex(level, output));
            assertEquals(1, WorkerRecipeCatalog.producingRecipes(level, output).size());
            clearInvocations(planks);
            assertEquals(4, lookup(scheduler, level, plank).producing(plank).getFirst().resultAmount());
            verify(planks, never()).getIngredients();
            verify(unrelated, never()).getIngredients();
            verify(opaque, never()).test(any());
            verify(manager, times(1)).getRecipes();
            WorkerRecipeCatalog.invalidate();
            when(planks.getResultItem(any())).thenReturn(new ItemStack(Items.OAK_PLANKS, 8));
            WorkerRecipeIndex rebuilt = lookup(scheduler, level, output);
            assertNotSame(index, rebuilt);
            assertEquals(8, rebuilt.producing(plank).getFirst().resultAmount());
            assertNotSame(source, WorkerRecipeCatalog.deferredSource(level));
        }
    }

    // Do not spend thousands of owner queries on variants for a machine that is absent
    @Test void skipsIngredientVariantsWhenNoProcessorCanRunTheRecipe(){
        Level level = mock(Level.class);
        when(level.getRecipeManager()).thenReturn(mock(RecipeManager.class));
        List<WorkerResourceKey> alternatives = new java.util.ArrayList<>();
        for(int idx = 0; idx < 10_000; idx++)
            alternatives.add(new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.parse("test:input_" + idx)));
        var recipe = new WorkerRecipeDefinition(ResourceLocation.parse("test:missing_machine"),
                ResourceLocation.withDefaultNamespace("smelting"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipeDefinition.Ingredient(alternatives, 1)),
                new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace("iron_ingot")), 1);
        Thread owner = Thread.currentThread();
        java.util.concurrent.atomic.AtomicInteger routeChecks = new java.util.concurrent.atomic.AtomicInteger();
        try(var scheduler = new DeferredWorkScheduler()){
            var res = scheduler.submit(() -> WorkerRecipeCatalog.supportedDeferred(scheduler, level, recipe,
                    plan -> {
                        assertSame(owner, Thread.currentThread());
                        routeChecks.incrementAndGet();
                        return false;
                    }, plan -> { throw new AssertionError("An absent machine must not probe ingredient variants"); }));
            drain(scheduler, res);
            assertFalse(res.join());
            assertEquals(1, routeChecks.get());
            routeChecks.set(0);
            var routable = scheduler.submit(() -> WorkerRecipeCatalog.routableDeferred(scheduler, level, recipe, plan -> {
                assertSame(owner, Thread.currentThread());
                routeChecks.incrementAndGet();
                return true;
            }));
            drain(scheduler, routable);
            assertTrue(routable.join());
            assertEquals(1, routeChecks.get());
            var available = new WorkerRecipeDefinition(recipe.recipeId(), recipe.processorType(), recipe.operation(),
                    List.of(new WorkerRecipeDefinition.Ingredient(alternatives.subList(0, 3), 1)), recipe.result(), 1);
            var supported = scheduler.submit(() -> WorkerRecipeCatalog.supportedDeferred(scheduler, level, available,
                    plan -> true, plan -> plan.inputs().getFirst().resource().equals(alternatives.get(2))));
            drain(scheduler, supported);
            assertTrue(supported.join());
        }
    }

    // Exercise the game event path instead of manually advancing a detached scheduler
    @Test void serverEventsAdvanceLookupAndCancelItWhenTheWorldStops(){
        var server = mock(net.minecraft.server.MinecraftServer.class);
        var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        bus.start();
        bus.register(DeferredWorkScheduler.class);
        try(var scheduler = DeferredWorkScheduler.forServer(server)){
            Thread owner = Thread.currentThread();
            var res = scheduler.submit(() -> scheduler.onOwnerThread(() -> {
                assertSame(owner, Thread.currentThread());
                return 37;
            }));
            long deadline = System.nanoTime() + 5_000_000_000L;
            while(!res.isDone()){
                assertTrue(System.nanoTime() < deadline, "Server ticks did not advance lookup");
                bus.post(new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(() -> true, server));
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }
            assertEquals(37, res.join());
            assertTrue(scheduler.ticks() > 0L);
            var pending = scheduler.submit(() -> scheduler.onOwnerThread(() -> 42));
            bus.post(new net.neoforged.neoforge.event.server.ServerStoppedEvent(server));
            assertTrue(pending.isCancelled());
            try(var next = DeferredWorkScheduler.forServer(server)){
                assertNotSame(scheduler, next);
            }
        }finally{ bus.unregister(DeferredWorkScheduler.class); }
    }

    private static WorkerRecipeIndex lookup(DeferredWorkScheduler scheduler, Level level, WorkerResourceKey output){
        long deadline = System.nanoTime() + 15_000_000_000L;
        while(true){
            try{ return WorkerRecipeCatalog.deferredIndex(level, output); }
            catch(DeferredWorkScheduler.Pending pending){
                assertTrue(System.nanoTime() < deadline, "Requested recipe lookup did not finish");
                scheduler.tick();
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }
        }
    }

    private static void drain(DeferredWorkScheduler scheduler, java.util.concurrent.CompletableFuture<?> res){
        drain(scheduler, res, 5_000_000_000L);
    }

    private static void drain(DeferredWorkScheduler scheduler, java.util.concurrent.CompletableFuture<?> res, long timeout){
        long deadline = System.nanoTime() + timeout;
        while(!res.isDone()){
            assertTrue(System.nanoTime() < deadline, "Recipe index did not finish");
            scheduler.tick();
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
    }

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
