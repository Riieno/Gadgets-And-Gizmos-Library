package com.rieno.gadgetsandgizmos.lib.worker;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Rank enabled workers by their remaining queued work
public final class WorkerAssignmentRanker{
    private WorkerAssignmentRanker(){}

    // Restrict an explicit choice or put idle workers before the shortest active queue
    public static List<WorkerStatusSnapshot> ordered(Collection<WorkerStatusSnapshot> workers,
                                                     @Nullable WorkerResourceType type,
                                                     @Nullable UUID selectedWorkerId){
        if(workers == null) return List.of();
        return workers.stream()
                .filter(worker -> worker != null && worker.enabled())
                .filter(worker -> type == null || worker.job().accepts(type))
                .filter(worker -> selectedWorkerId == null || selectedWorkerId.equals(worker.workerId()))
                .sorted(Comparator.comparingDouble(WorkerAssignmentRanker::remainingWork)
                        .thenComparing(worker -> worker.workerId().toString()))
                .toList();
    }

    // Estimate each planned stage as one unit and the current stage by its remaining fraction
    public static double remainingWork(WorkerStatusSnapshot worker){
        if(worker == null) return Double.POSITIVE_INFINITY;
        double remaining = worker.plannedOrders().size();
        WorkerWorkOrder current = worker.currentOrder();
        if(current != null){
            WorkerTask task = current.task();
            remaining += Math.max(0.001D, (double) task.remainingAmount()
                    / Math.max(1L, task.requestedAmount()));
        }
        return remaining;
    }
}
