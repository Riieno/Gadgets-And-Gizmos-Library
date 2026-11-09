package com.rieno.gadgetsandgizmos.lib.client.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

// Align horizontal panel textures with their existing rotated UVs
public final class HorizontalPanelConnectedTextureBehaviour extends StateConnectedTextureBehaviour{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain the horizontal panel sprite shifts
    public HorizontalPanelConnectedTextureBehaviour(CTSpriteShiftEntry... shifts){
        super(shifts);
    }

    // Check whether the front surface is covered
    @Override
    protected boolean isBeingBlocked(BlockState state, BlockAndTintGetter reader,
                                     BlockPos pos, BlockPos otherPos, Direction face){
        if(!state.hasProperty(HorizontalDirectionalBlock.FACING)){
            return super.isBeingBlocked(state, reader, pos, otherPos, face);
        }
        return state.getValue(HorizontalDirectionalBlock.FACING) == face
                && super.isBeingBlocked(state, reader, pos, otherPos, face);
    }

    // Check if the UVs should be reversed
    @Override
    protected boolean reverseUVs(BlockState state, Direction face){
        Direction.Axis axis = state.getValue(HorizontalDirectionalBlock.FACING).getAxis();
        if(axis == Direction.Axis.X){
            return face.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                    && face.getAxis() != Direction.Axis.X;
        }
        return face != Direction.NORTH
                && face.getAxisDirection() != Direction.AxisDirection.POSITIVE;
    }

    // Check if the UVs should be reversed vertically
    @Override
    protected boolean reverseUVsVertically(BlockState state, Direction face){
        Direction.Axis axis = state.getValue(HorizontalDirectionalBlock.FACING).getAxis();
        if(axis == Direction.Axis.X && face == Direction.NORTH
                || axis == Direction.Axis.Z && face == Direction.WEST){
            return false;
        }
        return super.reverseUVsVertically(state, face);
    }

    // Match vertical connections to the rotated panel UVs
    @Override
    protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos,
                                       BlockState state, Direction face){
        Direction.Axis axis = state.getValue(HorizontalDirectionalBlock.FACING).getAxis();
        boolean alongX = axis == Direction.Axis.X;
        if(face.getAxis().isVertical() && alongX){
            return super.getUpDirection(reader, pos, state, face).getClockWise();
        }
        if(face.getAxis() == axis || face.getAxis().isVertical()){
            return super.getUpDirection(reader, pos, state, face);
        }
        return Direction.fromAxisAndDirection(axis,
                alongX ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    // Match horizontal connections to the rotated panel UVs
    @Override
    protected Direction getRightDirection(BlockAndTintGetter reader, BlockPos pos,
                                          BlockState state, Direction face){
        Direction.Axis axis = state.getValue(HorizontalDirectionalBlock.FACING).getAxis();
        if(face.getAxis().isVertical() && axis == Direction.Axis.X){
            return super.getRightDirection(reader, pos, state, face).getClockWise();
        }
        if(face.getAxis() == axis || face.getAxis().isVertical()){
            return super.getRightDirection(reader, pos, state, face);
        }
        return Direction.fromAxisAndDirection(Direction.Axis.Y, face.getAxisDirection());
    }
}
