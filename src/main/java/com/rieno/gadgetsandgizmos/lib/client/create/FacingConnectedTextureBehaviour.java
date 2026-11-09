package com.rieno.gadgetsandgizmos.lib.client.create;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

// Rotate horizontal surface textures with a model's horizontal facing
public class FacingConnectedTextureBehaviour extends StateConnectedTextureBehaviour{
    // Use standard box UVs with the supplied surface shifts
    public FacingConnectedTextureBehaviour(CTSpriteShiftEntry... shifts){
        super(shifts);
    }

    // Join only touching geometry so separated slabs and panels retain their outer borders
    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader,
                              BlockPos pos, BlockPos otherPos, Direction face,
                              Direction primaryOffset, Direction secondaryOffset){
        if(state != other) return false;
        var shape = state.getShape(reader, pos);
        if(shape.isEmpty()) return false;
        for(Direction.Axis axis : Direction.Axis.values()){
            int delta = axis.choose(otherPos.getX() - pos.getX(), otherPos.getY() - pos.getY(), otherPos.getZ() - pos.getZ());
            if(delta != 0 && (shape.min(axis) > 0 || shape.max(axis) < 1)) return false;
        }
        return true;
    }

    // Follow the rotated model's vertical texture axis on its top and bottom
    @Override
    protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face){
        Direction dir = super.getUpDirection(reader, pos, state, face);
        return face.getAxis().isVertical() ? rotate(state, dir) : dir;
    }

    // Follow the rotated model's horizontal texture axis on its top and bottom
    @Override
    protected Direction getRightDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face){
        Direction dir = super.getRightDirection(reader, pos, state, face);
        return face.getAxis().isVertical() ? rotate(state, dir) : dir;
    }

    // Apply the same clockwise rotation used by north-facing block models
    private static Direction rotate(BlockState state, Direction dir){
        if(!state.hasProperty(HorizontalDirectionalBlock.FACING)) return dir;
        return switch(state.getValue(HorizontalDirectionalBlock.FACING)){
            case EAST -> dir.getClockWise();
            case SOUTH -> dir.getOpposite();
            case WEST -> dir.getCounterClockWise();
            default -> dir;
        };
    }
}
