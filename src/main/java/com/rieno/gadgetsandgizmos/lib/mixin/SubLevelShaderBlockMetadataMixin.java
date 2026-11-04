package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.client.render.TerrainBlockMetadata;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;

// Supply material ids missing from Sable's vanilla terrain compiler under Sodium
@Mixin(BlockRenderDispatcher.class)
public abstract class SubLevelShaderBlockMetadataMixin{
    // Keep the normal world compiler and its shader metadata owner unchanged
    @WrapMethod(method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V")
    private void renderShaderBlock(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poses,
                                   VertexConsumer vertices, boolean sides, RandomSource random, ModelData data,
                                   RenderType type, Operation<Void> original){
        if(!SubLevelBlockEntityCollector.isSubLevelPlotPosition(Minecraft.getInstance().level, pos)){
            original.call(state, pos, level, poses, vertices, sides, random, data, type);
            return;
        }
        TerrainBlockMetadata.renderBlock(level, state, pos, vertices,
                () -> original.call(state, pos, level, poses, vertices, sides, random, data, type));
    }
}
