package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.rieno.gadgetsandgizmos.lib.probe.LoadedBlockAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

// Check loaded ownership before controllers access a ship's native physics body
public final class SablePhysicsBodyApi{
    private SablePhysicsBodyApi(){}

    // Check native membership, including the interval before Java marks a body removed
    public static boolean isLoaded(@Nullable PhysicsPipeline pipeline, @Nullable PhysicsPipelineBody body){
        return pipeline != null && body != null && !body.isRemoved()
                && (!(pipeline instanceof PhysicsBodyAvailability available) || available.isBodyLoaded(body));
    }

    // Require the current loaded sublevel and native body without requesting any chunks
    public static boolean isLoaded(@Nullable ServerSubLevel body){
        if(body == null || body.isRemoved() || body.getLevel() == null || body.getPlot() == null
                || body.getPlot().getLoadedChunks().isEmpty()) return false;
        var container = SubLevelContainer.getContainer(body.getLevel());
        return container != null && container.getSubLevel(body.getUniqueId()) == body
                && container.physicsSystem() != null && isLoaded(container.physicsSystem().getPipeline(), body);
    }

    // Require the controller's existing chunk and exact block entity, including hosted components
    public static boolean isBlockEntityLoaded(@Nullable BlockEntity entity){
        if(entity == null || entity.isRemoved()) return false;
        BlockEntity host = HostedBlockEntities.host(entity);
        if(host != null) entity = host;
        if(entity.isRemoved() || !(entity.getLevel() instanceof ServerLevel level)) return false;
        var pos = entity.getBlockPos();
        var body = SableLevelApi.containing(entity);
        if(body instanceof ServerSubLevel server && !isLoaded(server)) return false;
        return LoadedBlockAccess.blockEntity(level, body == null ? null : body.getUniqueId(), pos) == entity;
    }
}
