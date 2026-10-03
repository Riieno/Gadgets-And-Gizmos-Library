package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorkerStatusSnapshotTest{
    @Test void remoteSerializationBoundsPlannedOrdersWithoutChangingLocalSnapshot(){
        var orders = new ArrayList<WorkerWorkOrder>();
        for(int idx = 0; idx < 12; idx++) orders.add(WorkerWorkOrder.automatic(new WorkerTask(null, "Order " + idx, null, 1, 0, 0, true)));
        var status = new WorkerStatusSnapshot(UUID.randomUUID(), "Worker", WorkerProfile.Job.ANY, true,
                null, BlockPos.ZERO, "Working", "", orders.getFirst(), orders);
        var remote = status.toTag(3);
        assertEquals(12, remote.getInt("PlannedTotal"));
        assertEquals(3, remote.getList("Planned", 10).size());
        assertEquals(12, status.plannedOrders().size());
        assertEquals(12, status.toTag().getList("Planned", 10).size());
    }

    @Test void remoteSnapshotCarriesCompletedAndWaitingSteps(){
        var completed = WorkerWorkOrder.automatic(new WorkerTask(null, "Smelt copper", null, 4, 4, 1, true));
        var waiting = WorkerWorkOrder.automatic(new WorkerTask(null, "Press copper", null, 4, 0, 1, true));
        var status = new WorkerStatusSnapshot(UUID.randomUUID(), "Worker", WorkerProfile.Job.ANY, true,
                null, BlockPos.ZERO, "Working", "", null, java.util.List.of(waiting),
                java.util.List.of(completed), java.util.List.of(waiting.id()));
        var restored = WorkerStatusSnapshot.fromTag(status.toTag(4));
        assertEquals(completed.id(), restored.completedOrders().getFirst().id());
        assertEquals(java.util.List.of(waiting.id()), restored.waitingOrders());
        assertEquals(4L, restored.completedWeight());
        assertEquals(1, restored.completedTotal());
    }
}
