package com.rieno.gadgetsandgizmos.lib.discovery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

// Read the current actor at a plot position, returning null when no block entity exists
public interface SubLevelActorLookup{
    @Nullable BlockEntity gadgetsngizmos$actorBlockEntity(BlockPos pos);
}
