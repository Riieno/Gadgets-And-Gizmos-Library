package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class WorkerDispatcherTest {
    // A full worker must wait instead of extracting an item it cannot carry
    @Test void doesNotDispatchWithoutCargoSpace(){
        WorkerTask task = new WorkerTask(UUID.randomUUID(), "Full worker", WorkerResourceKey.energy(), 1024L, 0L, 0, true);
        var source = new Endpoint(BlockPos.ZERO, 4096L, 0L);
        var target = new Endpoint(new BlockPos(2, 0, 0), 0L, 4096L);
        assertFalse(WorkerDispatcher.assign(task, List.of(source, target), Vec3.ZERO, 0L).assigned());
        assertFalse(WorkerDispatcher.assign(WorkerWorkOrder.automatic(task), List.of(source, target), Vec3.ZERO, 0L).assigned());
    }

    @Test
    void assignsNearestSourceAndReceiverWithinAllLimits() {
        WorkerResourceKey coal = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("coal"));
        WorkerTask task = new WorkerTask(UUID.randomUUID(), "Coal", coal,
                6400L, 0L, 0, true);
        Endpoint farSource = new Endpoint(new BlockPos(30, 0, 0), 64L, 0L);
        Endpoint nearSource = new Endpoint(new BlockPos(2, 0, 0), 48L, 0L);
        Endpoint target = new Endpoint(new BlockPos(5, 0, 0), 0L, 32L);

        WorkerDispatcher.Assignment assignment = WorkerDispatcher.assign(task,
                List.of(farSource, nearSource, target), Vec3.ZERO, 64L);

        assertTrue(assignment.assigned());
        assertSame(nearSource, assignment.source());
        assertSame(target, assignment.target());
        assertEquals(32L, assignment.amount());
    }

    @Test void routesOnlyToManagedDestinationsAndPrefersMatchingStorage(){
        WorkerResourceKey copper = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("copper_ingot"));
        WorkerTask task = new WorkerTask(UUID.randomUUID(), "Sort copper", copper, 8L, 0L, 0, true);
        Endpoint source = new Endpoint(BlockPos.ZERO, 8L, 0L);
        WorkerEndpoint ordinary = destination(copper, new BlockPos(1, 0, 0), false, 0);
        WorkerEndpoint open = destination(copper, new BlockPos(2, 0, 0), true, 2);
        WorkerEndpoint filtered = destination(copper, new BlockPos(8, 0, 0), true, 0);
        var assignment = WorkerDispatcher.assign(task, List.of(source, ordinary, open, filtered),
                Vec3.ZERO, 8L);
        assertSame(filtered, assignment.target());
    }

    @Test
    void workerConfigurationRoundTripsThroughNbt() {
        WorkerProfile profile = new WorkerProfile(UUID.randomUUID(), "Loader",
                WorkerProfile.Job.ITEMS, true);
        WorkerTask task = new WorkerTask(UUID.randomUUID(), "Coal",
                new WorkerResourceKey(WorkerResourceType.ITEM,
                        ResourceLocation.withDefaultNamespace("coal")),
                6400L, 128L, 10, true);

        assertEquals(profile, WorkerProfile.fromTag(profile.toTag()));
        assertEquals(task, WorkerTask.fromTag(task.toTag()));

        WorkerWorkOrder order = new WorkerWorkOrder(UUID.randomUUID(), task,
                WorkerWorkOrder.Mode.PROCESS, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new WorkerResourceKey(WorkerResourceType.ITEM,
                        ResourceLocation.withDefaultNamespace("iron_ingot")), 16L);
        WorkerEndpointSnapshot snapshot = new WorkerEndpointSnapshot(UUID.randomUUID(), null,
                new BlockPos(4, 5, 6), "Input Vault", "createthrusters:smart_vault",
                true, true, List.of(new WorkerEndpointSnapshot.ResourceAmount(
                task.resource(), 128L, 256L)));

        assertEquals(order, WorkerWorkOrder.fromTag(order.toTag()));
        assertEquals(snapshot, WorkerEndpointSnapshot.fromTag(snapshot.toTag()));
    }

    @Test
    void explicitWorkOrderEndpointsOverrideNearestSelection() {
        WorkerResourceKey coal = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("coal"));
        WorkerTask task = new WorkerTask(UUID.randomUUID(), "Coal", coal,
                64L, 0L, 0, true);
        Endpoint nearSource = new Endpoint(new BlockPos(1, 0, 0), 64L, 0L);
        Endpoint requestedSource = new Endpoint(new BlockPos(20, 0, 0), 64L, 0L);
        Endpoint requestedTarget = new Endpoint(new BlockPos(24, 0, 0), 0L, 64L);
        WorkerWorkOrder order = new WorkerWorkOrder(UUID.randomUUID(), task,
                WorkerWorkOrder.Mode.TRANSFER, requestedSource.id(), requestedTarget.id(), null,
                coal, 0L);

        WorkerDispatcher.WorkAssignment assignment = WorkerDispatcher.assign(order,
                List.of(nearSource, requestedSource, requestedTarget), Vec3.ZERO, 64L);

        assertTrue(assignment.assigned());
        assertSame(requestedSource, assignment.source());
        assertSame(requestedTarget, assignment.target());
        assertEquals(64L, assignment.amount());
    }

    @Test void permitsExplicitFuelDeliveryOnlyIntoMachineInputs(){
        WorkerResourceKey coal = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("coal"));
        WorkerTask task = new WorkerTask(UUID.randomUUID(), "Refuel", coal, 4L, 0L, 0, true);
        Endpoint source = new Endpoint(BlockPos.ZERO, 4L, 0L);
        WorkerEndpoint machine = destination(coal, new BlockPos(2, 0, 0), false, 0);
        UUID machineId = UUID.randomUUID();
        when(machine.id()).thenReturn(machineId);
        var order = new WorkerWorkOrder(UUID.randomUUID(), task, WorkerWorkOrder.Mode.TRANSFER,
                source.id(), machineId, null, coal, 4L);
        assertFalse(WorkerDispatcher.assign(order, List.of(source, machine), Vec3.ZERO, 4L).assigned());
        when(machine.acceptsProcessingInput()).thenReturn(true);
        assertSame(machine, WorkerDispatcher.assign(order, List.of(source, machine), Vec3.ZERO, 4L).target());
    }

    @Test
    void processingCanReturnOutputToTheOriginalSource() {
        WorkerResourceKey iron = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("iron_ingot"));
        WorkerResourceKey sheet = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.fromNamespaceAndPath("create", "iron_sheet"));
        WorkerTask task = new WorkerTask(UUID.randomUUID(), "Press Iron", iron,
                64L, 0L, 0, true);
        Endpoint barrel = new Endpoint(new BlockPos(1, 0, 0), 64L, 64L);
        Endpoint depot = new Endpoint(new BlockPos(4, 0, 0), 0L, 64L);
        WorkerWorkOrder order = new WorkerWorkOrder(UUID.randomUUID(), task,
                WorkerWorkOrder.Mode.PROCESS, barrel.id(), barrel.id(), depot.id(), sheet, 64L);

        WorkerDispatcher.WorkAssignment assignment = WorkerDispatcher.assign(order,
                List.of(barrel, depot), Vec3.ZERO, 64L);

        assertTrue(assignment.assigned());
        assertSame(barrel, assignment.source());
        assertSame(depot, assignment.processor());
        assertSame(barrel, assignment.target());
    }

    @Test
    void workerCargoRetainsOnlyTheUnacceptedAmount() {
        WorkerResourcePacket packet = new WorkerResourcePacket(WorkerResourceKey.energy(), 1000L, null);

        WorkerResourcePacket remainder = packet.remainderAfter(600L);

        assertEquals(400L, remainder.amount());
        assertEquals(remainder, WorkerResourcePacket.fromTag(remainder.toTag()));
        assertTrue(packet.remainderAfter(1000L).isEmpty());
        assertFalse(packet.remainderAfter(-1L).isEmpty());
    }

    @Test
    void groundAdvanceDoesNotInterpolateVerticallyWhileWalking() {
        Vec3 current = new Vec3(0.0D, 2.0D, 0.0D);
        Vec3 target = new Vec3(1.0D, 3.0D, 0.0D);

        Vec3 moving = WorkerPathing.advanceGround(current, target, 0.25D);
        Vec3 reached = WorkerPathing.advanceGround(current, target, 1.0D);

        assertEquals(2.0D, moving.y);
        assertEquals(3.0D, reached.y);
        assertEquals(0.25D, moving.x);
    }

    @Test
    void workerTaskQueueRetainsActiveAndPlannedOrders() {
        WorkerTask firstTask = new WorkerTask(UUID.randomUUID(), "First", WorkerResourceKey.energy(),
                100L, 0L, 0, true);
        WorkerTask secondTask = new WorkerTask(UUID.randomUUID(), "Second", WorkerResourceKey.energy(),
                200L, 0L, 0, true);
        WorkerWorkOrder first = WorkerWorkOrder.automatic(firstTask);
        WorkerWorkOrder second = WorkerWorkOrder.automatic(secondTask);
        WorkerTaskQueue queue = new WorkerTaskQueue();

        assertTrue(queue.enqueue(first));
        assertTrue(queue.enqueue(second));
        assertFalse(queue.enqueue(first));

        WorkerTaskQueue restored = WorkerTaskQueue.fromTag(queue.toTag());
        assertEquals(first, restored.current());
        assertEquals(List.of(second), restored.planned());
        assertEquals(first, restored.completeCurrent());
        assertEquals(second, restored.current());
    }

    private record Endpoint(BlockPos position, long available, long space) implements WorkerEndpoint {
        @Override
        public UUID id() {
            return UUID.nameUUIDFromBytes(position.toShortString().getBytes());
        }

        @Override
        public UUID subLevelId() {
            return null;
        }

        @Override
        public boolean canExtract(WorkerResourceKey resource) {
            return available > 0L;
        }

        @Override
        public boolean canInsert(WorkerResourceKey resource) {
            return space > 0L;
        }

        @Override
        public long available(WorkerResourceKey resource) {
            return available;
        }

        @Override
        public long space(WorkerResourceKey resource) {
            return space;
        }

        @Override
        public WorkerResourcePacket extract(WorkerResourceKey resource, long maximumAmount, boolean simulate) {
            return new WorkerResourcePacket(resource, Math.min(available, maximumAmount), null);
        }

        @Override
        public long insert(WorkerResourcePacket packet, boolean simulate) {
            return Math.min(space, packet.amount());
        }
    }

    private static WorkerEndpoint destination(WorkerResourceKey resource, BlockPos pos,
                                              boolean accepts, int priority){
        WorkerEndpoint endpoint = mock(WorkerEndpoint.class);
        when(endpoint.position()).thenReturn(pos);
        when(endpoint.isAvailable()).thenReturn(true);
        when(endpoint.acceptsDelivery()).thenReturn(accepts);
        when(endpoint.canInsert(resource)).thenReturn(true);
        when(endpoint.space(resource)).thenReturn(64L);
        when(endpoint.insertionPriority(resource)).thenReturn(priority);
        return endpoint;
    }
}
