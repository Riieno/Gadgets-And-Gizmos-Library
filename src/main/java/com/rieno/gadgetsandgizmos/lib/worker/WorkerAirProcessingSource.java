package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

// Expose a machine's live processing field to worker recipe planning
public interface WorkerAirProcessingSource{
    // Check the actual recipe and processing position before assigning a worker
    boolean supportsWorkerRecipe(Level targetLevel, BlockPos target, WorkerRecipePlan plan);

    // Expose loaded input blocks reached by the processing field
    default List<BlockPos> workerProcessingTargets(Level targetLevel){
        return List.of();
    }
}
