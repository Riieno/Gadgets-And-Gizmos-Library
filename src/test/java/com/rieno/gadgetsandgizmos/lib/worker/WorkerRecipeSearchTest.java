package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRecipeSearchTest{
    @Test void prefersPressingOverMakingBlocksAndHammersEvenOnASharedBelt(){
        WorkerResourceKey iron = item("iron"), sticks = item("sticks"), block = item("block"),
                hammer = item("hammer"), sheet = item("sheet"), output = item("output");
        var blockRecipe = new WorkerRecipeDefinition(block.id(), item("crafting").id(), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(iron), 9L)), block, 1L);
        var hammerRecipe = new WorkerRecipeDefinition(hammer.id(), item("crafting").id(), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(block), 2L),
                        new WorkerRecipeDefinition.Ingredient(List.of(sticks), 3L)), hammer, 1L);
        var handRecipe = new WorkerRecipeDefinition(item("a_hammer_sheets").id(), item("crafting").id(),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(iron), 2L),
                        new WorkerRecipeDefinition.Ingredient(List.of(hammer), 1L)), sheet, 1L);
        var press = new WorkerRecipeDefinition(item("press").id(), item("pressing").id(), WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(iron), 1L)), sheet, 1L);
        var root = new WorkerRecipeDefinition(output.id(), item("crafting").id(), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(sheet), 4L)), output, 1L);
        var stock = Map.of(iron, 128L, sticks, 32L);
        var result = WorkerRecipePlanner.planFromStock(output, 1L, stock,
                new WorkerRecipeIndex(List.of(blockRecipe, hammerRecipe, handRecipe, press, root)), null,
                def -> true, plan -> true, null, plan -> plan.recipeId().equals(press.recipeId()) ? 8 : 0, null);
        assertTrue(result.chain().executable(), result.details().toString());
        assertEquals(List.of(press.recipeId(), root.recipeId()), result.chain().steps().stream().map(step -> step.plan().recipeId()).toList());
        verifyStock(result.chain(), stock);
    }

    @Test void comparesTransportCostsForStockedRecipeAlternatives(){
        WorkerResourceKey raw = item("raw"), output = item("output");
        var first = recipe("a_shared_line", output, raw);
        var second = recipe("b_direct", output, raw);
        var result = WorkerRecipePlanner.planFromStock(output, 1L, Map.of(raw, 1L),
                new WorkerRecipeIndex(List.of(first, second)), null, def -> true, plan -> true, null,
                plan -> plan.recipeId().equals(first.recipeId()) ? 8 : 0, null);
        assertEquals(second.recipeId(), result.chain().steps().getFirst().plan().recipeId());
    }

    @Test void avoidsExcessMaterialWhenOnlyOneOutputIsRequested(){
        WorkerResourceKey raw = item("raw"), output = item("output");
        var batch = new WorkerRecipeDefinition(item("a_batch").id(), item("crafting").id(), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(raw), 4L)), output, 4L);
        var single = recipe("b_single", output, raw);
        var result = search(output, 1L, Map.of(raw, 4L), List.of(batch, single));
        assertEquals(single.recipeId(), result.chain().steps().getFirst().plan().recipeId());
        var four = search(output, 4L, Map.of(raw, 4L), List.of(batch, single));
        assertEquals(batch.recipeId(), four.chain().steps().getFirst().plan().recipeId());
    }

    @Test void revisesPreferredRoutesWhenSeveralPrerequisiteMachinesAreUnavailable(){
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        var raw = item("raw");
        var prev = raw;
        for(int idx = 0; idx < 32; idx++){
            var output = item("stage_" + idx);
            definitions.add(recipe("a_blocked_" + idx, output, prev));
            definitions.add(recipe("b_available_" + idx, output, prev));
            prev = output;
        }
        var output = prev;
        var result = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> WorkerRecipePlanner.planFromStock(output, 1L,
                Map.of(raw, 1L), new WorkerRecipeIndex(definitions), null,
                def -> !def.recipeId().getPath().startsWith("a_blocked"), plan -> true, null, plan -> 0, null));
        assertTrue(result.chain().executable(), result.details().toString());
        assertEquals(32, result.chain().steps().size());
        assertTrue(result.chain().steps().stream().allMatch(step -> step.plan().recipeId().getPath().startsWith("b_available")));
        verifyStock(result.chain(), Map.of(raw, 1L));
    }

    @Test void resolvesAChainBeyondTheOldDepthCeiling(){
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        var raw = item("raw");
        var prev = raw;
        for(int idx = 0; idx < 256; idx++){
            var output = item("stage_" + idx);
            definitions.add(recipe("stage_" + idx, output, prev));
            prev = output;
        }
        var output = prev;
        var result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> search(output, 1L, Map.of(raw, 1L), definitions));
        assertTrue(result.chain().executable(), result.details().toString());
        assertEquals(256, result.chain().steps().size());
        verifyStock(result.chain(), Map.of(raw, 1L));
    }

    @Test void producesAnIngredientFromSeveralTagMembers(){
        WorkerResourceKey first = item("first"), second = item("second"), a = item("a"), b = item("b"), output = item("output");
        var root = new WorkerRecipeDefinition(output.id(), item("crafting").id(), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(a, b), 5L)), output, 1L);
        var stock = Map.of(first, 3L, second, 2L);
        var result = search(output, 1L, stock, List.of(recipe("a", a, first), recipe("b", b, second), root));
        assertTrue(result.chain().executable(), result.details().toString());
        verifyStock(result.chain(), stock);
    }

    @Test void finishesAPromisingChainBeforeComparingEveryCombination(){
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        var raw = item("raw");
        var prev = raw;
        for(int idx = 0; idx < 128; idx++){
            var output = item("stage_" + idx);
            for(int route = 0; route < 32; route++) definitions.add(recipe("route_" + idx + "_" + route, output, prev));
            prev = output;
        }
        var output = prev;
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        var res = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> WorkerRecipePlanner.planFromStock(output, 1L,
                Map.of(raw, 1L), new WorkerRecipeIndex(definitions), null, def -> {
                    checks.incrementAndGet();
                    return true;
                }, plan -> true, null, plan -> 0, (def, idx) -> 0L));
        assertTrue(res.chain().executable(), res.details().toString());
        assertTrue(checks.get() < 200, "Compared unused recipe alternatives: " + checks.get());
        verifyStock(res.chain(), Map.of(raw, 1L));
    }

    @Test void findsPreloadedMachineInputsBeyondAnUnreachableStockRank(){
        WorkerResourceKey raw = item("raw"), part = item("part"), output = item("output");
        var source = new WorkerRecipeIndex(List.of(recipe("part", part, raw), recipe("output", output, part)));
        var res = WorkerRecipePlanner.planFromStock(output, 1L, Map.of(), source, null, def -> true,
                plan -> true, null, plan -> 0, (def, idx) -> def.result().equals(part) ? 1L : 0L);
        assertTrue(res.chain().executable(), res.details().toString());
        assertEquals(List.of(part, output), res.chain().steps().stream().map(step -> step.plan().result()).toList());
    }

    @Test void allocatesOverlappingTagsAfterProducingAMissingSibling(){
        WorkerResourceKey raw = item("raw"), part = item("part"), a = item("a"), b = item("b"), c = item("c"), output = item("output");
        var root = new WorkerRecipeDefinition(output.id(), item("crafting").id(), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(part), 1L),
                        new WorkerRecipeDefinition.Ingredient(List.of(a, b), 2L),
                        new WorkerRecipeDefinition.Ingredient(List.of(a, c), 2L)), output, 1L);
        var stock = Map.of(raw, 1L, a, 2L, b, 1L, c, 1L);
        var res = search(output, 1L, stock, List.of(recipe("part", part, raw), root));
        assertTrue(res.chain().executable(), res.details().toString());
        verifyStock(res.chain(), stock);
    }

    @Test void retriesPreloadedAlternativesWhenTheStockedRouteIsQuantityStarved(){
        WorkerResourceKey raw = item("raw"), preloaded = item("preloaded"), part = item("part"), output = item("output");
        var installed = recipe("installed", part, preloaded);
        var root = new WorkerRecipeDefinition(output.id(), item("crafting").id(), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(part), 2L)), output, 1L);
        var source = new WorkerRecipeIndex(List.of(recipe("stocked", part, raw), installed, root));
        var res = WorkerRecipePlanner.planFromStock(output, 1L, Map.of(raw, 1L), source, null, def -> true,
                plan -> true, null, plan -> 0, (def, idx) -> def == installed ? 2L : 0L);
        assertTrue(res.chain().executable(), res.details().toString());
        assertEquals(installed.recipeId(), res.chain().steps().getFirst().plan().recipeId());
    }

    @Test void preservesSharedStockAcrossGeneratedPrerequisiteChoices(){
        Random random = new Random(93814L);
        for(int trial = 0; trial < 60; trial++){
            List<WorkerRecipeDefinition> definitions = new ArrayList<>();
            List<WorkerResourceKey> resources = new ArrayList<>();
            Map<WorkerResourceKey, Long> stock = new HashMap<>();
            for(int idx = 0; idx < 3; idx++){
                var raw = item("raw_" + idx);
                resources.add(raw);
                stock.put(raw, 1L + random.nextInt(3));
            }
            for(int idx = 3; idx < 8; idx++){
                var output = item("part_" + idx);
                for(int route = 0; route < 2; route++){
                    List<WorkerRecipeDefinition.Ingredient> inputs = new ArrayList<>();
                    for(int slot = 0; slot < 1 + random.nextInt(2); slot++)
                        inputs.add(new WorkerRecipeDefinition.Ingredient(List.of(resources.get(random.nextInt(idx))), 1L));
                    definitions.add(new WorkerRecipeDefinition(item("recipe_" + idx + "_" + route).id(), item("crafting").id(),
                            WorkerRecipePlan.Operation.WORKER_CRAFTING, inputs, output, 1L + random.nextInt(2)));
                }
                resources.add(output);
            }
            var output = resources.getLast();
            var exhaustive = WorkerRecipePlanner.planDetailed(output, 1L, stock, definitions, null, plan -> true);
            var result = search(output, 1L, stock, definitions);
            if(exhaustive.chain().executable()) assertTrue(result.chain().executable(), "Trial " + trial + ": " + result.details());
            if(result.chain().executable()) verifyStock(result.chain(), stock);
        }
    }

    static void verifyStock(WorkerRecipeChain chain, Map<WorkerResourceKey, Long> available){
        Map<WorkerResourceKey, Long> stock = new HashMap<>(available);
        for(var step : chain.steps()){
            var plan = step.plan();
            long batches = (step.requestedAmount() - 1L) / plan.resultAmount() + 1L;
            var ingredients = plan.inputs().stream().map(input -> new WorkerRecipeDefinition.Ingredient(input.alternatives(), input.amount())).toList();
            var allocation = WorkerIngredientAllocation.allocate(ingredients,
                    ingredients.stream().map(input -> input.amount() * batches).toList(), stock);
            assertNotNull(allocation, "Double-reserved ingredients for " + plan.recipeId());
            for(var slot : allocation) slot.forEach((resource, amount) -> stock.compute(resource, (key, val) -> val - amount));
            stock.merge(plan.result(), batches * plan.resultAmount(), Long::sum);
        }
    }

    private static WorkerRecipePlanner.Result search(WorkerResourceKey output, long amount,
                                                       Map<WorkerResourceKey, Long> stock, List<WorkerRecipeDefinition> definitions){
        return WorkerRecipePlanner.planFromStock(output, amount, stock, new WorkerRecipeIndex(definitions), null,
                def -> true, plan -> true, null, plan -> 0, (def, idx) -> 0L);
    }
    private static WorkerResourceKey item(String id){ return new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace(id)); }
    private static WorkerRecipeDefinition recipe(String id, WorkerResourceKey output, WorkerResourceKey input){
        return new WorkerRecipeDefinition(item(id).id(), item("crafting").id(), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(input), 1L)), output, 1L);
    }
}
