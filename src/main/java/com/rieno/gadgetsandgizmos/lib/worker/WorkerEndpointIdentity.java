package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

// Preserve stable endpoint identities across worker, inventory and request integrations
public final class WorkerEndpointIdentity{
    private WorkerEndpointIdentity(){}

    public static UUID of(ResourceLocation dimension, UUID subLevelId, BlockPos pos, Direction side){
        String key = dimension + ":" + subLevelId + ":" + pos.asLong() + ":" + side;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
