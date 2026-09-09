package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;

// Render Create kinetic models in off-screen previews where Flywheel suppresses their normal renderer.
final class CreateKineticPreviewRenderer {
    private CreateKineticPreviewRenderer() {
    }

    // Render the rotating model for a compatible Create kinetic block entity.
    static boolean render(
            BlockEntity entity,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        if (!(entity instanceof KineticBlockEntity kinetic)
                || state == null || !(state.getBlock() instanceof IRotate)) {
            return false;
        }
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        ChunkRenderTypeSet renderTypes = model.getRenderTypes(
                state, RandomSource.create(42L), ModelData.EMPTY);
        RenderType selected = RenderType.cutoutMipped();
        for (RenderType candidate : RenderType.chunkBufferLayers()) {
            if (renderTypes.contains(candidate)) {
                selected = candidate;
            }
        }
        KineticBlockEntityRenderer.renderRotatingKineticBlock(
                kinetic, state, pose, buffers.getBuffer(selected), light);
        return true;
    }
}
