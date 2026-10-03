package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerAssignmentRankerTest{
    private static WorkerStatusSnapshot snapshot(UUID id, WorkerProfile.Job job, boolean enabled,
                                                 WorkerWorkOrder current, List<WorkerWorkOrder> planned){
        return new WorkerStatusSnapshot(id, "Worker", job, enabled, null,
                BlockPos.ZERO, "", "", current, planned);
    }

    private static WorkerWorkOrder order(long completed){
        WorkerTask task = new WorkerTask(null, "Move items",
                new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace("stone")),
                100, completed, 0, true);
        return WorkerWorkOrder.automatic(task);
    }

    @Test void idleWorkerPrecedesBusyWorker(){
        WorkerStatusSnapshot busy = snapshot(UUID.randomUUID(), WorkerProfile.Job.ITEMS, true, order(90), List.of());
        WorkerStatusSnapshot idle = snapshot(UUID.randomUUID(), WorkerProfile.Job.ITEMS, true, null, List.of());
        assertEquals(idle.workerId(), WorkerAssignmentRanker.ordered(
                List.of(busy, idle), WorkerResourceType.ITEM, null).getFirst().workerId());
    }

    @Test void remainingProgressAndQueuedStagesDetermineBusyOrder(){
        WorkerStatusSnapshot near = snapshot(UUID.randomUUID(), WorkerProfile.Job.ITEMS, true, order(90), List.of());
        WorkerStatusSnapshot far = snapshot(UUID.randomUUID(), WorkerProfile.Job.ITEMS, true, order(10), List.of());
        WorkerStatusSnapshot queued = snapshot(UUID.randomUUID(), WorkerProfile.Job.ITEMS, true,
                order(99), List.of(order(0)));
        assertEquals(List.of(near.workerId(), far.workerId(), queued.workerId()),
                WorkerAssignmentRanker.ordered(List.of(queued, far, near), WorkerResourceType.ITEM, null)
                        .stream().map(WorkerStatusSnapshot::workerId).toList());
    }

    @Test void explicitChoiceStillRequiresEnabledCompatibleWorker(){
        WorkerStatusSnapshot idle = snapshot(UUID.randomUUID(), WorkerProfile.Job.ITEMS, true, null, List.of());
        WorkerStatusSnapshot selected = snapshot(UUID.randomUUID(), WorkerProfile.Job.ITEMS, true,
                order(0), List.of());
        WorkerStatusSnapshot paused = snapshot(UUID.randomUUID(), WorkerProfile.Job.ITEMS, false,
                null, List.of());
        WorkerStatusSnapshot fluids = snapshot(UUID.randomUUID(), WorkerProfile.Job.FLUIDS, true,
                null, List.of());
        List<WorkerStatusSnapshot> roster = List.of(idle, selected, paused, fluids);
        assertEquals(selected.workerId(), WorkerAssignmentRanker.ordered(roster,
                WorkerResourceType.ITEM, selected.workerId()).getFirst().workerId());
        assertTrue(WorkerAssignmentRanker.ordered(roster, WorkerResourceType.ITEM, paused.workerId()).isEmpty());
        assertTrue(WorkerAssignmentRanker.ordered(roster, WorkerResourceType.ITEM, fluids.workerId()).isEmpty());
        assertTrue(WorkerAssignmentRanker.ordered(roster, WorkerResourceType.ITEM, UUID.randomUUID()).isEmpty());
    }
}
