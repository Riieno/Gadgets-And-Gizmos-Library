package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;

// Select deterministic loaded endpoints for one worker task
public final class WorkerDispatcher {
    // Initialize the worker dispatcher
    private WorkerDispatcher() {
    }

    // Assign the nearest usable source and destination
    public static Assignment assign(WorkerTask task, Collection<? extends WorkerEndpoint> endpoints,
                                    Vec3 workerPosition, long carryingLimit) {
        if (task == null || !task.pending() || endpoints == null || endpoints.isEmpty()) {
            return Assignment.NONE;
        }
        Vec3 origin = workerPosition == null ? Vec3.ZERO : workerPosition;
        WorkerResourceKey resource = task.resource();
        WorkerEndpoint source = endpoints.stream()
                .filter(endpoint -> endpoint != null && endpoint.canExtract(resource)
                        && endpoint.available(resource) > 0L)
                .min(Comparator.comparingDouble(endpoint -> distanceSqr(origin, endpoint)))
                .orElse(null);
        if (source == null) return Assignment.NONE;
        Vec3 sourcePosition = Vec3.atCenterOf(source.position());
        WorkerEndpoint target = endpoints.stream()
                .filter(endpoint -> endpoint != null && endpoint != source
                        && endpoint.canInsert(resource) && endpoint.space(resource) > 0L)
                .min(Comparator.comparingDouble(endpoint -> distanceSqr(sourcePosition, endpoint)))
                .orElse(null);
        if (target == null) return Assignment.NONE;
        long amount = Math.min(task.remainingAmount(), Math.max(1L, carryingLimit));
        amount = Math.min(amount, source.available(resource));
        amount = Math.min(amount, target.space(resource));
        return amount <= 0L ? Assignment.NONE : new Assignment(source, target, amount);
    }

    // Assign endpoints while respecting optional explicit work-order routing
    public static WorkAssignment assign(WorkerWorkOrder order,
                                        Collection<? extends WorkerEndpoint> endpoints,
                                        Vec3 workerPosition, long carryingLimit) {
        if (order == null || order.task() == null || !order.task().pending()
                || endpoints == null || endpoints.isEmpty()) return WorkAssignment.NONE;
        WorkerTask task = order.task();
        Vec3 origin = workerPosition == null ? Vec3.ZERO : workerPosition;
        WorkerEndpoint source = selectEndpoint(endpoints, order.sourceEndpointId(), origin,
                endpoint -> endpoint.canExtract(task.resource()) && endpoint.available(task.resource()) > 0L);
        if (source == null) return WorkAssignment.NONE;
        WorkerEndpoint processor = null;
        Vec3 nextOrigin = Vec3.atCenterOf(source.position());
        if (order.processing()) {
            processor = selectEndpoint(endpoints, order.processorEndpointId(), nextOrigin,
                    endpoint -> endpoint.canInsert(task.resource()) && endpoint.space(task.resource()) > 0L);
            if (processor == null) return WorkAssignment.NONE;
            nextOrigin = Vec3.atCenterOf(processor.position());
        }
        WorkerResourceKey deliveredResource = order.processing()
                ? order.outputResource() : task.resource();
        WorkerEndpoint excludedProcessor = processor;
        WorkerEndpoint target = selectEndpoint(endpoints, order.destinationEndpointId(), nextOrigin,
                endpoint -> (order.processing() || endpoint != source) && endpoint != excludedProcessor
                        && endpoint.canInsert(deliveredResource) && endpoint.space(deliveredResource) > 0L);
        if (target == null) return WorkAssignment.NONE;
        long amount = Math.min(task.remainingAmount(), Math.max(1L, carryingLimit));
        amount = Math.min(amount, source.available(task.resource()));
        if (order.processing()) amount = Math.min(amount, processor.space(task.resource()));
        else amount = Math.min(amount, target.space(deliveredResource));
        return amount <= 0L ? WorkAssignment.NONE : new WorkAssignment(source, processor, target, amount);
    }

    // Select an explicit endpoint or the nearest endpoint matching a predicate
    private static WorkerEndpoint selectEndpoint(Collection<? extends WorkerEndpoint> endpoints,
                                                 UUID requestedId, Vec3 origin,
                                                 java.util.function.Predicate<WorkerEndpoint> predicate) {
        return endpoints.stream()
                .filter(endpoint -> endpoint != null
                        && (requestedId == null || requestedId.equals(endpoint.id()))
                        && predicate.test(endpoint))
                .min(Comparator.comparingDouble(endpoint -> distanceSqr(origin, endpoint)))
                .orElse(null);
    }

    // Get a root-coordinate distance estimate for deterministic selection
    private static double distanceSqr(Vec3 position, WorkerEndpoint endpoint) {
        return position.distanceToSqr(Vec3.atCenterOf(endpoint.position()));
    }

    // Store an endpoint assignment
    public record Assignment(WorkerEndpoint source, WorkerEndpoint target, long amount) {
        public static final Assignment NONE = new Assignment(null, null, 0L);

        // Check whether the assignment is usable
        public boolean assigned() {
            return source != null && target != null && amount > 0L;
        }
    }

    // Store a transfer or processing endpoint assignment
    public record WorkAssignment(WorkerEndpoint source, WorkerEndpoint processor,
                                 WorkerEndpoint target, long amount) {
        public static final WorkAssignment NONE = new WorkAssignment(null, null, null, 0L);

        // Check whether the assignment is usable
        public boolean assigned() {
            return source != null && target != null && amount > 0L;
        }
    }
}
