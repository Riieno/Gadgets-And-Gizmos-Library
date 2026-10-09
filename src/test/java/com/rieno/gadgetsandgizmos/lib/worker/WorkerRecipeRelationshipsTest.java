package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRecipeRelationshipsTest{
    @Test void stopsPreloadProbesAtAnUnavailableMandatoryIngredient(){
        WorkerResourceKey missing = item("missing"), result = item("result");
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        List<WorkerResourceKey> alternatives = new ArrayList<>();
        for(int idx = 0; idx < 10_000; idx++){
            var part = item("part_" + idx);
            alternatives.add(part);
            definitions.add(recipe("part_" + idx, part, item("raw_" + idx)));
        }
        definitions.add(new WorkerRecipeDefinition(result.id(), item("crafting").id(), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(missing), 1L),
                        new WorkerRecipeDefinition.Ingredient(alternatives, 1L)), result, 1L));
        var graph = new WorkerRecipeRelationships(definitions);
        AtomicInteger probes = new AtomicInteger();
        var ranks = graph.rank(List.of(result), Map.of(), def -> true, (def, idx) -> {
            probes.incrementAndGet();
            return 0L;
        }, true);
        assertFalse(ranks.productionCosts().containsKey(result));
        assertEquals(2, probes.get(), "Unrelated prerequisite branches must not query their machines");
    }

    @Test void retainsPreferredProducingRecipesWithoutFollowingConversionCycles(){
        WorkerResourceKey raw = item("raw"), part = item("part"), result = item("result");
        var first = recipe("part", part, raw);
        var last = recipe("result", result, part);
        var graph = new WorkerRecipeRelationships(List.of(recipe("cycle", part, result), first, last));
        var routes = graph.routes(List.of(result), Map.of(raw, 1L), def -> true, null, false);
        assertEquals(Map.of(part, first, result, last), routes.preferredRecipes());
        assertEquals(Map.of(part, 1, result, 2), routes.ranks().productionCosts());
        assertThrows(UnsupportedOperationException.class, () -> routes.preferredRecipes().clear());
        assertTrue(graph.routes(List.of(result), Map.of(raw, 1L), def -> def != first, null, false)
                .preferredRecipes().isEmpty());
    }

    // Thousands of cyclic tag members must not hide a stocked route behind the old tree budget
    @Test void resolvesDenseCyclicTagsFromStock(){
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        List<WorkerResourceKey> alternatives = new ArrayList<>();
        WorkerResourceKey raw = item("raw"), part = item("part"), result = item("result");
        for(int idx = 0; idx < 20_000; idx++){
            WorkerResourceKey first = item("cycle_" + idx), second = item("return_" + idx);
            alternatives.add(first);
            definitions.add(recipe("cycle_" + idx, first, second));
            definitions.add(recipe("return_" + idx, second, first));
        }
        alternatives.add(part);
        definitions.add(recipe("part", part, raw));
        definitions.add(new WorkerRecipeDefinition(result.id(), item("crafting").id(),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(alternatives, 1L)), result, 1L));
        WorkerRecipeIndex index = new WorkerRecipeIndex(definitions);
        var planned = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> plan(index, result, Map.of(raw, 1L)));
        assertTrue(planned.chain().executable(), planned.failure().message());
        assertEquals(List.of(part, result), planned.chain().steps().stream().map(step -> step.plan().result()).toList());
        assertSame(index.relationships(List.of(result)), index.relationships(List.of(part)));
    }

    // Stocked overlapping tags need a capacity allocation, even when each slot requests millions of items
    @Test void allocatesLargeOverlappingTagsWithoutEnumeratingQuantities(){
        WorkerResourceKey first = item("first"), second = item("second"), result = item("result");
        var inputs = List.of(new WorkerRecipeDefinition.Ingredient(List.of(first, second), 1_000_000_000L),
                new WorkerRecipeDefinition.Ingredient(List.of(first), 1_000_000_000L));
        var def = new WorkerRecipeDefinition(result.id(), item("crafting").id(),
                WorkerRecipePlan.Operation.WORKER_CRAFTING, inputs, result, 1L);
        var planned = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> plan(new WorkerRecipeIndex(List.of(def)),
                result, Map.of(first, 1_000_000_000L, second, 1_000_000_000L)));
        assertTrue(planned.chain().executable(), planned.failure().message());
        assertEquals(second, planned.chain().steps().getFirst().plan().inputs().getFirst().resource());
        assertNull(WorkerIngredientAllocation.allocate(inputs, List.of(1_000_000_001L, 1_000_000_000L),
                Map.of(first, 1_000_000_000L, second, 1_000_000_000L)));
    }

    // An input held in a machine can seed a route without making it extractable stock
    @Test void propagatesMachineCreditsAndRejectsUnsupportedRoutes(){
        WorkerResourceKey raw = item("raw"), part = item("part"), result = item("result");
        var first = recipe("part", part, raw);
        var last = recipe("result", result, part);
        var graph = new WorkerRecipeRelationships(List.of(first, last));
        var credited = graph.rank(List.of(result), Map.of(), def -> true,
                (def, idx) -> def == first ? 1L : 0L);
        assertEquals(1, credited.productionCosts().get(part));
        assertEquals(2, credited.productionCosts().get(result));
        assertTrue(graph.rank(List.of(result), Map.of(raw, 1L), def -> def != first,
                (def, idx) -> 0L).productionCosts().isEmpty());
    }

    // A large producer bucket must not exhaust a tree budget before its usable machine variant
    @Test void findsStockedMachineVariantBeyondTheOldTreeLimit(){
        WorkerResourceKey raw = item("raw"), result = item("result");
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        for(int idx = 0; idx < 40_000; idx++) definitions.add(recipe("variant_" + idx, result, raw));
        definitions.add(recipe("z_usable", result, raw));
        var planned = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> WorkerRecipePlanner.planFromStock(result,
                1L, Map.of(raw, 1L), new WorkerRecipeIndex(definitions), null, def -> true,
                candidate -> candidate.recipeId().getPath().equals("z_usable"), null, candidate -> 0, (def, idx) -> 0L));
        assertTrue(planned.chain().executable(), planned.failure().message());
        assertEquals("z_usable", planned.chain().steps().getFirst().plan().recipeId().getPath());
    }

    // Verify the shared map at pack scale without making the routine test suite allocate a large heap
    @Test @EnabledIfSystemProperty(named = "gg.worker.scale", matches = "true")
    void indexesAMillionRecipesAndVisitsOnlyTheRequestedRelationships(){
        WorkerResourceKey raw = item("raw"), part = item("part"), result = item("result");
        List<WorkerRecipeDefinition> definitions = new ArrayList<>(1_000_002);
        definitions.add(recipe("part", part, raw));
        definitions.add(recipe("result", result, part));
        var ingredient = new WorkerRecipeDefinition.Ingredient(List.of(item("unavailable")), 1L);
        for(int idx = 0; idx < 1_000_000; idx++){
            ResourceLocation id = item("unrelated_" + idx).id();
            definitions.add(new WorkerRecipeDefinition(id, item("crafting").id(),
                    WorkerRecipePlan.Operation.WORKER_CRAFTING, List.of(ingredient),
                    new WorkerResourceKey(WorkerResourceType.ITEM, id), 1L));
        }
        var graph = new WorkerRecipeRelationships(definitions);
        AtomicInteger checks = new AtomicInteger();
        var ranks = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> graph.rank(List.of(result), Map.of(raw, 1L),
                def -> { checks.incrementAndGet(); return true; }, (def, idx) -> 0L));
        assertEquals(Map.of(part, 1, result, 2), ranks.productionCosts());
        assertEquals(2, checks.get());
        WorkerRecipeSource source = new WorkerRecipeSource(){
            @Override public List<WorkerRecipeDefinition> producing(WorkerResourceKey resource){ return graph.producing(resource); }
            @Override public WorkerRecipeRelationships relationships(java.util.Collection<WorkerResourceKey> outputs){ return graph; }
        };
        checks.set(0);
        var planned = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> WorkerRecipePlanner.planFromStock(result,
                1L, Map.of(raw, 1L), source, null, def -> { checks.incrementAndGet(); return true; },
                plan -> true, null, plan -> 0, (def, idx) -> 0L));
        assertTrue(planned.chain().executable(), planned.failure().message());
        assertEquals(2, checks.get());
    }

    private static WorkerRecipePlanner.Result plan(WorkerRecipeIndex index, WorkerResourceKey output,
                                                    Map<WorkerResourceKey, Long> available){
        return WorkerRecipePlanner.planFromStock(output, 1L, available, index, null,
                def -> true, candidate -> true, null, candidate -> 0, (def, idx) -> 0L);
    }

    private static WorkerResourceKey item(String id){
        return new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace(id));
    }

    private static WorkerRecipeDefinition recipe(String id, WorkerResourceKey result, WorkerResourceKey input){
        return new WorkerRecipeDefinition(item(id).id(), item("crafting").id(), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(input), 1L)), result, 1L);
    }
}
