package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

// Expose kinetic rotation only through one configured shaft face
public final class SingleFaceRotationConfiguration implements IRotate {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Face
    private final Direction face;
    // Active state predicate
    private final Predicate<BlockState> activeStatePredicate;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the single face rotation configuration
    public SingleFaceRotationConfiguration(Direction face, Predicate<BlockState> activeStatePredicate) {
        this.face = face;
        this.activeStatePredicate = activeStatePredicate;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return activeStatePredicate.test(state) && this.face == face;
    }

    // Get the rotation axis
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return face.getAxis();
    }
}
