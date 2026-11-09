package com.rieno.gadgetsandgizmos.lib.client.create;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

// Map connected faces to packed atlas regions or multiple texture patches
public interface ConnectedTextureQuadMapper{
    // Return replacement geometry for one matching source quad and its texture context
    List<BakedQuad> mapQuad(BlockState state, BakedQuad quad, CTSpriteShiftEntry shift, int connections);
}
