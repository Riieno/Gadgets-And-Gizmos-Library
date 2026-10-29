package com.rieno.gadgetsandgizmos.lib.client.render;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import net.minecraft.world.level.block.state.BlockState;

// Select presentation-only block states without changing the source world
public final class HologramBlockModels{
    private HologramBlockModels(){}

    public static BlockState closedTank(BlockState state){
        return state.getBlock() instanceof FluidTankBlock && state.hasProperty(FluidTankBlock.SHAPE)
                ? state.setValue(FluidTankBlock.SHAPE, FluidTankBlock.Shape.PLAIN) : state;
    }

    public static boolean opaque(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos){
        return state.getBlock() instanceof FluidTankBlock || state.isSolidRender(level, pos);
    }
}
