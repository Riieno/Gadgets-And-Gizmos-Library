package com.rieno.gadgetsandgizmos.lib.client.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// Retain independent texture contexts for every layer and every potentially visible face
public final class ConnectedTextureModel extends BakedModelWrapper<BakedModel>{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep texture data local to this wrapper so nested models cannot overwrite it
    private final ModelProperty<int[][]> textureData = new ModelProperty<>();
    private final List<ConnectedTextureBehaviour> behaviours;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Copy the texture layers in their application order
    public ConnectedTextureModel(BakedModel model, ConnectedTextureBehaviour... behaviours){
        super(Objects.requireNonNull(model, "model"));
        this.behaviours = List.of(behaviours);
    }

    // Gather all faces because schematic and Ponder sections can expose previously hidden faces
    @Override
    public ModelData getModelData(BlockAndTintGetter reader, BlockPos pos, BlockState state, ModelData data){
        ModelData base = originalModel.getModelData(reader, pos, state, data);
        int[][] indices = new int[behaviours.size()][Direction.values().length];
        for(int idx = 0; idx < behaviours.size(); idx++){
            Arrays.fill(indices[idx], -1);
            var behaviour = behaviours.get(idx);
            for(Direction face : Direction.values()){
                var type = behaviour.getDataType(reader, pos, state, face);
                if(type == null) continue;
                var ctx = behaviour.buildContext(reader, pos, state, face, type.getContextRequirement());
                indices[idx][face.get3DDataValue()] = type.getTextureIndex(ctx);
            }
        }
        return base.derive().with(textureData, indices).build();
    }

    // Shift each matching quad once without changing the cached source geometry
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType){
        List<BakedQuad> quads = originalModel.getQuads(state, side, rand, data, renderType);
        int[][] indices = data.get(textureData);
        if(state == null || indices == null || quads.isEmpty()) return quads;
        List<BakedQuad> res = new ArrayList<>(quads.size());
        quadLoop: for(BakedQuad quad : quads){
            BakedQuad shifted = quad;
            for(int idx = 0; idx < behaviours.size(); idx++){
                int tile = indices[idx][quad.getDirection().get3DDataValue()];
                if(tile < 0) continue;
                var shift = behaviours.get(idx).getShift(state, rand, quad.getDirection(), quad.getSprite());
                if(shift == null || quad.getSprite() != shift.getOriginal()) continue;
                if(behaviours.get(idx) instanceof ConnectedTextureQuadMapper mapper){
                    res.addAll(mapper.mapQuad(state, quad, shift, tile));
                    continue quadLoop;
                }
                int[] vertices = quad.getVertices().clone();
                for(int vertex = 0; vertex < 4; vertex++){
                    BakedQuadHelper.setU(vertices, vertex, shift.getTargetU(BakedQuadHelper.getU(vertices, vertex), tile));
                    BakedQuadHelper.setV(vertices, vertex, shift.getTargetV(BakedQuadHelper.getV(vertices, vertex), tile));
                }
                shifted = new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), shift.getTarget(),
                        quad.isShade(), quad.hasAmbientOcclusion());
                break;
            }
            res.add(shifted);
        }
        return res;
    }
}
