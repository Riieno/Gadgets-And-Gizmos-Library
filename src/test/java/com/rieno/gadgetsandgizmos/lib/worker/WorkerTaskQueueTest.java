package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkerTaskQueueTest{
    // A supplied intermediate must cover every later recipe, not just the current batch
    @Test void countsAllPlannedInputDemand(){
        var pipe = order("fluid_pipe").outputResource();
        var thruster = order("thruster");
        var oxidizer = order("fuel_oxidizer");
        var type = ResourceLocation.withDefaultNamespace("crafting");
        var queue = new WorkerTaskQueue();
        queue.enqueueAll(List.of(order("fluid_pipe"),
                thruster.withRecipePlan(new WorkerRecipePlan(type, type, WorkerRecipePlan.Operation.CRAFTING,
                        List.of(new WorkerRecipePlan.Input(pipe, 4)), thruster.outputResource(), 1)),
                oxidizer.withRecipePlan(new WorkerRecipePlan(type, type, WorkerRecipePlan.Operation.CRAFTING,
                        List.of(new WorkerRecipePlan.Input(pipe, 2)), oxidizer.outputResource(), 1))));
        assertEquals(6L, queue.plannedInputDemand(pipe));
    }

    // Run repaired prerequisites before the original request and all later queued work
    @Test void prependsDependenciesAndPreservesQueueAcrossSave(){
        var current = order("crafting_table");
        var later = order("chest");
        var logs = order("oak_log");
        var planks = order("oak_planks");
        var queue = new WorkerTaskQueue();
        queue.enqueueAll(List.of(current, later));
        assertTrue(queue.prepend(List.of(logs, planks)));
        queue = WorkerTaskQueue.fromTag(queue.toTag());
        assertEquals(List.of(logs, planks, current, later), List.of(queue.completeCurrent(),
                queue.completeCurrent(), queue.completeCurrent(), queue.completeCurrent()));
        assertNull(queue.current());
    }

    // Reject duplicate repairs without disturbing the original queue
    @Test void rejectsConflictingPrerequisitesAtomically(){
        var current = order("crafting_table");
        var later = order("chest");
        var queue = new WorkerTaskQueue();
        queue.enqueueAll(List.of(current, later));
        assertFalse(queue.prepend(List.of(order("oak_planks"), later)));
        assertEquals(current, queue.current());
        assertEquals(List.of(later), queue.planned());
    }

    // Compile every dependency while keeping delivery and return metadata on the final order
    @Test void compilesAllStepsBeforeTheRequestedDelivery(){
        var plank = order("oak_planks").outputResource();
        var table = order("crafting_table").outputResource();
        var type = ResourceLocation.withDefaultNamespace("crafting");
        var chain = new WorkerRecipeChain(List.of(
                new WorkerRecipeChain.Step(new WorkerRecipePlan(plank.id(), type,
                        WorkerRecipePlan.Operation.WORKER_CRAFTING, List.of(), plank, 4), 4),
                new WorkerRecipeChain.Step(new WorkerRecipePlan(table.id(), type,
                        WorkerRecipePlan.Operation.WORKER_CRAFTING,
                        List.of(new WorkerRecipePlan.Input(plank, 4)), table, 1), 1)));
        UUID request = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID station = UUID.randomUUID();
        var orders = chain.orders(request, "Table", 3, recipient, station);
        assertEquals(2, orders.size());
        assertNull(orders.getFirst().destinationEndpointId());
        assertNull(orders.getFirst().returnStationId());
        assertEquals(request, orders.getLast().id());
        assertEquals(recipient, orders.getLast().destinationEndpointId());
        assertEquals(station, orders.getLast().returnStationId());
        assertEquals(1, orders.getLast().task().requestedAmount());
    }

    @Test void prioritizesNewRequestsWithoutSplittingRecipeChains(){
        var running = order("iron_ingot");
        var later = order("oak_log");
        var urgentFirst = prioritized("copper_ingot", 10);
        var urgentSecond = prioritized("copper_sheet", 10);
        var queue = new WorkerTaskQueue();
        queue.enqueueAll(List.of(running, later));
        queue.enqueueAll(List.of(urgentFirst, urgentSecond));
        assertEquals(running, queue.current());
        assertEquals(List.of(urgentFirst, urgentSecond, later), queue.planned());
    }

    @Test void reusesACompiledRoutineAfterItsPreviousCycleFinishes(){
        List<WorkerWorkOrder> compiled = List.of(order("oak_planks"), order("crafting_table"));
        var queue = new WorkerTaskQueue();
        assertTrue(queue.enqueueAll(compiled));
        assertEquals(compiled.getFirst(), queue.completeCurrent());
        assertEquals(compiled.getLast(), queue.completeCurrent());
        assertTrue(queue.enqueueAll(compiled));
        assertEquals(compiled.getFirst(), queue.current());
        assertEquals(List.of(compiled.getLast()), queue.planned());
        assertTrue(queue.completed().isEmpty());
    }

    @Test void runsIndependentWorkDuringMachineWaitAndRetainsCompletedSteps(){
        var smelting = prioritized("iron_ingot", 2);
        var planks = prioritized("oak_planks", 1);
        var dependent = recipeOrder("iron_block", "iron_ingot", 1);
        var queue = new WorkerTaskQueue();
        queue.enqueueAll(List.of(smelting, planks, dependent));
        assertTrue(queue.yieldCurrent(Set.of(smelting.id())));
        assertEquals(planks, queue.current());
        assertEquals(List.of(smelting, dependent), queue.planned());
        assertTrue(queue.promote(smelting.id()));
        assertEquals(smelting, queue.current());
        assertEquals(List.of(planks, dependent), queue.planned());
        assertEquals(smelting, queue.completeCurrent());
        queue = WorkerTaskQueue.fromTag(queue.toTag());
        assertEquals(List.of(smelting), queue.completed());
        assertEquals(1L, queue.completedWeight());
        assertEquals(1, queue.completedTotal());
        assertEquals(planks, queue.current());
    }

    @Test void doesNotStartARecipeThatNeedsParkedOutput(){
        var smelting = prioritized("iron_ingot", 1);
        var dependent = recipeOrder("iron_block", "iron_ingot", 20);
        var queue = new WorkerTaskQueue();
        queue.enqueueAll(List.of(smelting, dependent));
        assertFalse(queue.yieldCurrent(Set.of(smelting.id())));
        assertEquals(smelting, queue.current());
    }

    @Test void recordsFinishedCraftBeforeItsReturnTrip(){
        var craft = prioritized("crafting_table", 3);
        var queue = new WorkerTaskQueue();
        queue.enqueue(craft);
        queue.recordCompleted(craft.withTask(craft.task().complete(1)));
        queue.updateCurrent(WorkerWorkOrder.returnToStation(craft.id(),
                new WorkerTask(craft.id(), "Return to Pod", WorkerResourceKey.energy(), 1, 0, 3, true),
                UUID.randomUUID()));
        queue = WorkerTaskQueue.fromTag(queue.toTag());
        assertEquals(craft.id(), queue.completed().getFirst().id());
        assertEquals(1L, queue.completedWeight());
        assertEquals("Return to Pod", queue.current().task().name());
    }

    private static WorkerWorkOrder prioritized(String item, int priority){
        WorkerWorkOrder order = order(item);
        return order.withTask(new WorkerTask(order.id(), "Craft", order.task().resource(), 1, 0, priority, true));
    }

    private static WorkerWorkOrder recipeOrder(String output, String input, int priority){
        WorkerWorkOrder order = prioritized(output, priority);
        WorkerResourceKey ingredient = order(input).outputResource();
        return order.withRecipePlan(new WorkerRecipePlan(ResourceLocation.withDefaultNamespace(output),
                ResourceLocation.withDefaultNamespace("crafting"), WorkerRecipePlan.Operation.CRAFTING,
                List.of(new WorkerRecipePlan.Input(ingredient, 1)), order.outputResource(), 1));
    }

    private static WorkerWorkOrder order(String item){
        UUID id = UUID.randomUUID();
        var resource = new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace(item));
        return new WorkerWorkOrder(id, new WorkerTask(id, "Craft", resource, 1, 0, 0, true),
                WorkerWorkOrder.Mode.AUTO_CRAFT, null, null, null, resource, 1);
    }
}
