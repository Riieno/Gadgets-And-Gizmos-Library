package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Immediate Create kinetic rendering for off-screen preview targets. */
final class CreateKineticPreviewRenderer {
    private CreateKineticPreviewRenderer() {
    }

    static boolean render(BlockEntity blockEntity, BlockState state, PoseStack pose,
                          MultiBufferSource buffers, int light) {
        if (!(blockEntity instanceof KineticBlockEntity kinetic) || !(state.getBlock() instanceof IRotate)
                || blockEntity.getLevel() == null || !VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!"create".equals(id.getNamespace())) return false;
        return switch (id.getPath()) {
            case "shaft", "cogwheel" -> {
                whole(kinetic, state, pose, buffers, light);
                yield true;
            }
            case "large_cogwheel" -> {
                Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(kinetic);
                Direction facing = Direction.get(Direction.AxisDirection.POSITIVE, axis);
                var vertices = buffers.getBuffer(RenderType.solid());
                SuperByteBuffer wheel = CachedBuffers.partialFacingVertical(
                        (PartialModel) AllPartialModels.SHAFTLESS_LARGE_COGWHEEL, state, facing);
                KineticBlockEntityRenderer.renderRotatingBuffer(kinetic, wheel, pose, vertices, light);
                SuperByteBuffer shaft = CachedBuffers.partialFacingVertical(
                        (PartialModel) AllPartialModels.COGWHEEL_SHAFT, state, facing);
                KineticBlockEntityRenderer.kineticRotationTransform(shaft, kinetic, axis,
                        KineticBlockEntityRenderer.getAngleForBe(kinetic, kinetic.getBlockPos(), axis), light)
                        .renderInto(pose, vertices);
                yield true;
            }
            case "flywheel" -> {
                Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(kinetic);
                whole(kinetic, KineticBlockEntityRenderer.shaft(axis), pose, buffers, light);
                whole(kinetic, state, pose, buffers, light);
                yield true;
            }
            default -> false;
        };
    }

    private static void whole(KineticBlockEntity kinetic, BlockState state, PoseStack pose,
                              MultiBufferSource buffers, int light) {
        KineticBlockEntityRenderer.renderRotatingKineticBlock(
                kinetic, state, pose, buffers.getBuffer(RenderType.solid()), light);
    }
}
