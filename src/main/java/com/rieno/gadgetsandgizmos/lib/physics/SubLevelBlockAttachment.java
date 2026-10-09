package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

// Let a block travel with its support during native sublevel transfers
public interface SubLevelBlockAttachment{
    // Check whether this block is attached to the neighbor in the supplied direction
    boolean isAttachedTo(BlockState state, Direction supportDir);
}
