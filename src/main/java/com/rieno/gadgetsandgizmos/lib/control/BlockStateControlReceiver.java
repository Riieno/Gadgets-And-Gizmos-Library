package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.world.level.block.state.BlockState;

// Let a block entity apply graph state writes through its normal control lifecycle
public interface BlockStateControlReceiver{
    boolean applyControlledBlockState(BlockState state);
}
