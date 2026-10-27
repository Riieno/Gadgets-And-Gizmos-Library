package com.rieno.gadgetsandgizmos.lib.hologram;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

// Share Minecraft's shape-aware face tests across terrain, cutaways and moving projections
public final class HologramFaceVisibility{
    private HologramFaceVisibility(){}

    public static boolean shouldRenderFace(BlockState state, BlockGetter level, BlockPos pos,
                                           Direction dir, boolean neighborIncluded){
        if(!neighborIncluded) return true;
        BlockPos next = pos.relative(dir);
        BlockState other = level.getBlockState(next);
        boolean fluid = state.getRenderShape() == RenderShape.INVISIBLE && !state.getFluidState().isEmpty();
        boolean otherFluid = other.getRenderShape() == RenderShape.INVISIBLE && !other.getFluidState().isEmpty();
        if(fluid && state.getFluidState().getType().isSame(other.getFluidState().getType())) return false;
        if(otherFluid && other.getFluidState().isSource()) return false;
        if(fluid && other.isSolidRender(level, next)) return false;
        return Block.shouldRenderFace(state, level, pos, dir, next);
    }
}
