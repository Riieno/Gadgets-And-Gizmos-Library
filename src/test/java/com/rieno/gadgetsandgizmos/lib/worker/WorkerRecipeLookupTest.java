package com.rieno.gadgetsandgizmos.lib.worker;

import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRecipeLookupTest{
    @Test void sharesCachedPlansAndRechecksChangedStockAndMachineContext(){
        try(var scheduler = new DeferredWorkScheduler()){
            WorkerResourceKey raw = item("raw"), part = item("part"), result = item("result");
            var index = new WorkerRecipeIndex(List.of(recipe("part", raw, part), recipe("result", part, result)));
            var lookup = new WorkerRecipeLookup();
            AtomicInteger checks = new AtomicInteger();
            Thread owner = Thread.currentThread();
            var support = (WorkerRecipeLookup.RecipeSupport)(def, work) -> work.onOwnerThread(() -> {
                assertSame(owner, Thread.currentThread());
                checks.incrementAndGet();
                return true;
            });
            Supplier<WorkerRecipePlanner.Result> request = () -> lookup.plan(scheduler, index, result, 1L,
                    Map.of(raw, 1L), null, "machine", support, plan -> true, null, plan -> 0, (def, idx) -> 0L);
            WorkerRecipePlanner.Result first = poll(scheduler, request);
            assertTrue(first.chain().executable());
            assertEquals(List.of(part, result), first.chain().steps().stream().map(step -> step.plan().result()).toList());
            int count = checks.get();
            assertSame(first, request.get());
            assertEquals(count, checks.get());
            var missing = poll(scheduler, () -> lookup.plan(scheduler, index, result, 1L, Map.of(), null,
                    "machine", support, plan -> true, null, plan -> 0, (def, idx) -> 0L));
            assertFalse(missing.chain().executable());
            var blocked = poll(scheduler, () -> lookup.plan(scheduler, index, result, 1L, Map.of(raw, 1L), null,
                    "removed machine", (def, work) -> false, plan -> false, null, plan -> 0, (def, idx) -> 0L));
            assertFalse(blocked.chain().executable());
        }
    }

    @Test void resolvesConcurrentWorkersWithoutRevisitingUnrelatedRecipes(){
        try(var scheduler = new DeferredWorkScheduler()){
            WorkerResourceKey raw = item("raw"), result = item("result");
            List<WorkerRecipeDefinition> definitions = new ArrayList<>();
            definitions.add(recipe("result", raw, result));
            for(int idx = 0; idx < 10_000; idx++) definitions.add(recipe("unused_" + idx,
                    item("unused_input_" + idx), item("unused_output_" + idx)));
            WorkerRecipeIndex index = new WorkerRecipeIndex(definitions);
            WorkerRecipeLookup lookup = new WorkerRecipeLookup();
            AtomicInteger checks = new AtomicInteger();
            List<Supplier<WorkerRecipePlanner.Result>> requests = new ArrayList<>();
            for(int idx = 0; idx < 8; idx++){
                String machine = "machine_" + idx;
                requests.add(() -> lookup.plan(scheduler, index, result, 1L, Map.of(raw, 1L), null, machine,
                        (def, work) -> work.onOwnerThread(() -> {
                            assertEquals(result, def.result());
                            checks.incrementAndGet();
                            return true;
                        }), plan -> true, null, plan -> 0, (def, slot) -> 0L));
            }
            for(var request : requests) assertThrows(DeferredWorkScheduler.Pending.class, request::get);
            for(var request : requests) assertTrue(poll(scheduler, request).chain().executable());
            assertEquals(8, checks.get());
        }
    }

    @Test void pendingRecipeOrdersSurviveReloadAndRemainCancellable(){
        WorkerTask task = new WorkerTask(UUID.randomUUID(), "Make result", item("result"), 12L, 0L, 4, true);
        WorkerWorkOrder order = WorkerWorkOrder.recipeLookup(task, WorkerRecipePlan.Operation.PROCESSING,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        WorkerTaskQueue queue = new WorkerTaskQueue();
        assertTrue(queue.enqueue(order));
        WorkerTaskQueue restored = WorkerTaskQueue.fromTag(queue.toTag());
        assertEquals(order, restored.current());
        assertTrue(restored.current().lookingUpRecipe());
        assertTrue(restored.cancel(task.id()));
        assertNull(restored.current());
    }

    @Test void keepsTheSelectedProcessorOnTheRecipeBeforeItsDelivery(){
        WorkerResourceKey raw = item("raw"), result = item("result");
        WorkerRecipePlan plan = new WorkerRecipePlan(ResourceLocation.withDefaultNamespace("result"),
                ResourceLocation.withDefaultNamespace("crafting"), WorkerRecipePlan.Operation.CRAFTING,
                List.of(new WorkerRecipePlan.Input(raw, 1L)), result, 1L);
        WorkerRecipeChain chain = new WorkerRecipeChain(List.of(new WorkerRecipeChain.Step(plan, 12L)));
        UUID processorId = UUID.randomUUID();
        var orders = chain.orders(UUID.randomUUID(), "Craft result", 0, UUID.randomUUID(), null, null, processorId);
        assertEquals(processorId, orders.getFirst().processorEndpointId());
        assertEquals(WorkerWorkOrder.Mode.TRANSFER, orders.getLast().mode());
        assertNull(orders.getLast().processorEndpointId());
    }

    // A stocked tag member must not cause searches through thousands of other recipe trees
    @Test void stockedTagSkipsOtherTreesAndRetainsTheCompletedPlan(){
        try(var scheduler = new DeferredWorkScheduler()){
            WorkerResourceKey result = item("result"), stocked = item("stocked");
            List<WorkerResourceKey> alternatives = new ArrayList<>();
            for(int idx = 0; idx < 10_000; idx++) alternatives.add(item("unused_" + idx));
            alternatives.add(stocked);
            var recipe = new WorkerRecipeDefinition(result.id(), item("crafting").id(),
                    WorkerRecipePlan.Operation.WORKER_CRAFTING,
                    List.of(new WorkerRecipeDefinition.Ingredient(alternatives, 1L)), result, 1L);
            AtomicInteger reads = new AtomicInteger();
            WorkerRecipeSource source = resource -> {
                assertEquals(result, resource, "A stocked ingredient must not trigger another recipe lookup");
                reads.incrementAndGet();
                return List.of(recipe);
            };
            WorkerRecipeLookup lookup = new WorkerRecipeLookup();
            AtomicInteger checks = new AtomicInteger();
            Supplier<WorkerRecipePlanner.Result> request = () -> lookup.planFromStock(scheduler, source,
                    result, 1L, Map.of(stocked, 1L), null, "machine", (def, work) -> {
                        checks.incrementAndGet();
                        return true;
                    }, plan -> true, null, plan -> 0, (def, idx) -> 0L);
            WorkerRecipePlanner.Result first = poll(scheduler, request);
            assertTrue(first.chain().executable());
            assertEquals(stocked, first.chain().steps().getFirst().plan().inputs().getFirst().resource());
            for(int tick = 0; tick < 1200; tick++) scheduler.tick();
            assertSame(first, request.get());
            assertEquals(1, reads.get());
            assertEquals(1, checks.get());
            var refreshed = poll(scheduler, () -> lookup.planFromStock(scheduler, source, result, 1L,
                    Map.of(stocked, 1L), null, new WorkerRecipeLookup.RequestContext("retry", "machine", true),
                    (def, work) -> false, plan -> false, null, plan -> 0, (def, idx) -> 0L));
            assertFalse(refreshed.chain().executable());
            assertEquals(2, reads.get());
            for(int tick = 0; tick < 21; tick++) scheduler.tick();
            assertTrue(poll(scheduler, request).chain().executable());
            assertEquals(3, reads.get());
        }
    }

    // Stop after a stocked tag supplies a batch instead of comparing thousands of valid distributions
    @Test void acceptsTheFirstCompleteStockedTagDistribution(){
        WorkerResourceKey result = item("result");
        List<WorkerResourceKey> alternatives = new ArrayList<>();
        Map<WorkerResourceKey, Long> available = new java.util.LinkedHashMap<>();
        for(int idx = 0; idx < 20; idx++){
            WorkerResourceKey resource = item("variant_" + idx);
            alternatives.add(resource);
            available.put(resource, 64L);
        }
        var recipe = new WorkerRecipeDefinition(result.id(), item("crafting").id(),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(alternatives, 1024L)), result, 1L);
        AtomicInteger checks = new AtomicInteger();
        var planned = WorkerRecipePlanner.planFromStock(result, 1L, available, resource -> {
            assertEquals(result, resource);
            return List.of(recipe);
        }, null, def -> true, plan -> {
            checks.incrementAndGet();
            return true;
        }, null, plan -> 0, (def, idx) -> 0L);
        assertTrue(planned.chain().executable());
        assertEquals(1, checks.get());
    }

    // A missing intermediate still needs backtracking when a later sibling uses the first raw material
    @Test void demandDrivenSearchBacktracksWithoutDiscoveringUnrelatedTrees(){
        WorkerResourceKey first = item("first"), second = item("second"), part = item("part"), result = item("result");
        var root = new WorkerRecipeDefinition(result.id(), item("crafting").id(),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(part), 1L),
                        new WorkerRecipeDefinition.Ingredient(List.of(first), 1L)), result, 1L);
        var firstPart = recipe("a_part", first, part);
        var secondPart = recipe("b_part", second, part);
        List<WorkerResourceKey> reads = new ArrayList<>();
        var planned = WorkerRecipePlanner.planFromStock(result, 1L, Map.of(first, 1L, second, 1L), resource -> {
            reads.add(resource);
            if(resource.equals(result)) return List.of(root);
            if(resource.equals(part)) return List.of(firstPart, secondPart);
            assertTrue(resource.equals(first) || resource.equals(second));
            return List.of();
        }, null, def -> true, plan -> true, null, plan -> 0, (def, idx) -> 0L);
        assertTrue(planned.chain().executable(), planned.failure().message());
        assertEquals(List.of(secondPart.recipeId(), root.recipeId()),
                planned.chain().steps().stream().map(step -> step.plan().recipeId()).toList());
        assertEquals(java.util.Set.of(result, part, first, second), new java.util.HashSet<>(reads));
        assertTrue(reads.size() <= 5);
    }

    // Loaded queues repair only missing inputs and retain the same repair while stock stays unchanged
    @Test void repairsSavedInputsFromStockAndRetainsTheRepair(){
        try(var scheduler = new DeferredWorkScheduler()){
            WorkerResourceKey raw = item("raw"), part = item("part"), result = item("result");
            var plan = new WorkerRecipePlan(result.id(), item("crafting").id(),
                    WorkerRecipePlan.Operation.WORKER_CRAFTING,
                    List.of(new WorkerRecipePlan.Input(part, 1L)), result, 1L);
            AtomicInteger reads = new AtomicInteger();
            WorkerRecipeSource source = resource -> {
                reads.incrementAndGet();
                if(resource.equals(result)) return List.of(recipe("result", part, result));
                if(resource.equals(raw)) return List.of();
                assertEquals(part, resource);
                return List.of(recipe("part", raw, part));
            };
            WorkerRecipeLookup lookup = new WorkerRecipeLookup();
            Supplier<WorkerRecipeChain> request = () -> lookup.prerequisitesFromStock(scheduler, source,
                    plan, 0, 0L, 1L, Map.of(raw, 1L), "machine", (def, work) -> true, candidate -> true);
            WorkerRecipeChain repaired = poll(scheduler, request);
            assertTrue(repaired.executable());
            assertEquals(List.of(part), repaired.steps().stream().map(step -> step.plan().result()).toList());
            int count = reads.get();
            for(int tick = 0; tick < 1200; tick++) scheduler.tick();
            assertSame(repaired, request.get());
            assertEquals(count, reads.get());
            var stocked = poll(scheduler, () -> lookup.prerequisitesFromStock(scheduler, source, plan, 0, 0L, 1L,
                    Map.of(part, 1L), "machine", (def, work) -> true, candidate -> true));
            assertTrue(stocked.steps().isEmpty());
        }
    }

    private static <T> T poll(DeferredWorkScheduler scheduler, Supplier<T> task){
        long deadline = System.nanoTime() + 5_000_000_000L;
        while(true){
            try{ return task.get(); }
            catch(DeferredWorkScheduler.Pending pending){
                assertTrue(System.nanoTime() < deadline, "Worker lookup did not finish");
                scheduler.tick();
                LockSupport.parkNanos(1_000_000L);
            }
        }
    }

    private static WorkerResourceKey item(String id){ return new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace(id)); }
    private static WorkerRecipeDefinition recipe(String id, WorkerResourceKey input, WorkerResourceKey output){
        return new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace(id), ResourceLocation.withDefaultNamespace("crafting"),
                WorkerRecipePlan.Operation.WORKER_CRAFTING, List.of(new WorkerRecipeDefinition.Ingredient(List.of(input), 1L)), output, 1L);
    }
}
