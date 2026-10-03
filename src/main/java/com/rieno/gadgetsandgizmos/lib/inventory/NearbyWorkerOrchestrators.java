package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerOrchestrator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;
import java.util.Objects;

// Find an accessible, loaded worker controller without a stored tablet binding
public final class NearbyWorkerOrchestrators{
    private NearbyWorkerOrchestrators(){}

    public static WorkerOrchestrator find(ServerLevel world, BlockEntity target, UUID subLevelId,
                                          ServerPlayer owner, int range){
        if(world == null || target == null || owner == null || range <= 0) return null;
        if(subLevelId != null && target.getLevel() instanceof ServerLevel local){
            WorkerOrchestrator res = search(world, local, subLevelId, target.getBlockPos(), owner, range);
            if(res != null) return res;
        }
        var body = SableLevelApi.subLevel(world, subLevelId);
        var point = SableTransformApi.toWorldPosition(body, target.getBlockPos().getCenter());
        return point == null ? null : search(world, world, null, BlockPos.containing(point), owner, range);
    }

    // Visit only existing block entities in already loaded chunks
    private static WorkerOrchestrator search(ServerLevel world, ServerLevel area, UUID subLevelId,
                                             BlockPos center, ServerPlayer owner, int range){
        int minX = (center.getX() - range) >> 4;
        int maxX = (center.getX() + range) >> 4;
        int minZ = (center.getZ() - range) >> 4;
        int maxZ = (center.getZ() + range) >> 4;
        WorkerOrchestrator res = null;
        double distance = (double) range * range;
        for(int x = minX; x <= maxX; x++) for(int z = minZ; z <= maxZ; z++){
            var chunk = area.getChunkSource().getChunkNow(x, z);
            if(chunk == null) continue;
            for(var be : chunk.getBlockEntities().values()){
                if(!(be instanceof WorkerOrchestrator workers) || be.isRemoved() || workers.managedWorkers().isEmpty()
                        || !Objects.equals(SableLevelApi.containingId(be), subLevelId)) continue;
                double next = be.getBlockPos().distSqr(center);
                if(next > distance || !WorldAccessPolicy.canAccessLocal(owner, world, subLevelId, be.getBlockPos())) continue;
                res = workers;
                distance = next;
            }
        }
        return res;
    }
}
