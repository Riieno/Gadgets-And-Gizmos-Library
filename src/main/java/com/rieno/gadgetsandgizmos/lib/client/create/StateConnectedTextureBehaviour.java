package com.rieno.gadgetsandgizmos.lib.client.create;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Join matching block states while selecting only registered source sprites
public class StateConnectedTextureBehaviour extends ConnectedTextureBehaviour.Base{
    // Retain immutable sprite choices for this surface
    private final List<CTSpriteShiftEntry> shifts;

    // Copy the sprite shifts used by this surface
    public StateConnectedTextureBehaviour(CTSpriteShiftEntry... shifts){
        this.shifts = List.of(shifts);
    }

    // Match actual quads while using the first shift to gather the shared context type
    @Override
    public @Nullable CTSpriteShiftEntry getShift(BlockState state, Direction face, @Nullable TextureAtlasSprite sprite){
        for(var shift : shifts) if(sprite == null || sprite == shift.getOriginal()) return shift;
        return null;
    }

    // Keep different orientations and alignments on separate surfaces
    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader,
                              BlockPos pos, BlockPos otherPos, Direction face,
                              @Nullable Direction primaryOffset, @Nullable Direction secondaryOffset){
        return state == other;
    }
}
