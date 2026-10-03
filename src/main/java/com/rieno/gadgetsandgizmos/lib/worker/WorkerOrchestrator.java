package com.rieno.gadgetsandgizmos.lib.worker;

import java.util.List;
import java.util.UUID;

// Expose managed workers and named tasks without exposing a controller implementation
public interface WorkerOrchestrator{
    // Expose only loaded storage bound to this controller; callers must apply viewer access checks
    default List<net.minecraft.world.level.block.entity.BlockEntity> linkedStorage(){ return List.of(); }

    List<WorkerStatusSnapshot> managedWorkers();
    List<String> workerTaskNames();
    WorkerFailureReason requestWorkerTask(WorkerTaskRequest request);
    WorkerFailureReason requestItems(WorkerItemRequest request);
    boolean cancelWorkerTask(UUID requestId);
    boolean setWorkerEnabled(UUID workerId, boolean enabled);
}
