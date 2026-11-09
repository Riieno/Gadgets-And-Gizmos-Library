package com.rieno.gadgetsandgizmos.lib.client.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// Connect the small vault source texture to its two multiblock atlases
public final class VaultConnectedTextureBehaviour extends ConnectedTextureBehaviour.Base{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain the medium and large atlases for each vault surface
    private final CTSpriteShiftEntry mediumTop;
    private final CTSpriteShiftEntry largeTop;
    private final CTSpriteShiftEntry mediumFront;
    private final CTSpriteShiftEntry largeFront;
    private final CTSpriteShiftEntry mediumSide;
    private final CTSpriteShiftEntry largeSide;
    private final CTSpriteShiftEntry mediumBottom;
    private final CTSpriteShiftEntry largeBottom;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vault behaviour
    public VaultConnectedTextureBehaviour(ResourceLocation folder){
        mediumTop = vaultShift(folder, "top", "medium");
        largeTop = vaultShift(folder, "top", "large");
        mediumFront = vaultShift(folder, "front", "medium");
        largeFront = vaultShift(folder, "front", "large");
        mediumSide = vaultShift(folder, "side", "medium");
        largeSide = vaultShift(folder, "side", "large");
        mediumBottom = vaultShift(folder, "bottom", "medium");
        largeBottom = vaultShift(folder, "bottom", "large");
    }

    // Create a vault sprite shift
    private static CTSpriteShiftEntry vaultShift(ResourceLocation folder, String face, String size){
        return CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,
                folder.withSuffix("/vault_" + face + "_small"),
                folder.withSuffix("/vault_" + face + "_" + size));
    }

    // Select the connected atlas for one vault face
    @Override
    public CTSpriteShiftEntry getShift(BlockState state, Direction face, TextureAtlasSprite sprite){
        Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        boolean large = state.getValue(ItemVaultBlock.LARGE);
        CTSpriteShiftEntry selected = face.getAxis() == axis
                ? (large ? largeFront : mediumFront)
                : face == Direction.UP
                ? (large ? largeTop : mediumTop)
                : face == Direction.DOWN
                ? (large ? largeBottom : mediumBottom)
                : (large ? largeSide : mediumSide);
        return sprite == null || sprite == selected.getOriginal() ? selected : null;
    }

    // Check whether two vault blocks belong to the same formed multiblock
    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader,
                              BlockPos pos, BlockPos otherPos, Direction face){
        return state == other && ConnectivityHandler.isConnected(reader, pos, otherPos);
    }

    // Get the upward texture direction
    @Override
    protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos,
                                       BlockState state, Direction face){
        Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
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

    // Get the rightward texture direction
    @Override
    protected Direction getRightDirection(BlockAndTintGetter reader, BlockPos pos,
                                          BlockState state, Direction face){
        Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        if(face.getAxis().isVertical() && axis == Direction.Axis.X){
            return super.getRightDirection(reader, pos, state, face).getClockWise();
        }
        if(face.getAxis() == axis || face.getAxis().isVertical()){
            return super.getRightDirection(reader, pos, state, face);
        }
        return Direction.fromAxisAndDirection(Direction.Axis.Y, face.getAxisDirection());
    }
}
