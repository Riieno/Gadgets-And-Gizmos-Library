package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltRenderer;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Draws Create belt partials when a live preview renders outside Flywheel's world pass. */
final class CreateBeltPreviewRenderer {
    private CreateBeltPreviewRenderer() {
    }

    /** Render one belt block's dynamic top, bottom, and pulley partials. */
    static boolean render(
            BlockEntity entity,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        return entity instanceof BeltBlockEntity belt
                && render(belt, state, pose, buffers, light);
    }

    /** Render one static belt block from a detached server preview snapshot. */
    static boolean render(
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        return render(null, state, pose, buffers, light);
    }

    // Render one belt with live animation when a client block entity is available.
    private static boolean render(
            BeltBlockEntity belt,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        if (state == null || !(state.getBlock() instanceof BeltBlock)) return false;
        BeltSlope slope = state.getValue(BeltBlock.SLOPE);
        BeltPart part = state.getValue(BeltBlock.PART);
        Direction facing = state.getValue(BeltBlock.HORIZONTAL_FACING);
        AxisDirection axisDirection = facing.getAxisDirection();
        boolean downward = slope == BeltSlope.DOWNWARD;
        boolean upward = slope == BeltSlope.UPWARD;
        boolean diagonal = downward || upward;
        boolean start = part == BeltPart.START;
        boolean end = part == BeltPart.END;
        boolean sideways = slope == BeltSlope.SIDEWAYS;
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        PoseStack transforms = new PoseStack();
        var stack = TransformStack.of(transforms);
        stack.center()
                .rotateYDegrees(net.createmod.catnip.math.AngleHelper.horizontalAngle(facing)
                        + (upward ? 180 : 0) + (sideways ? 270 : 0))
                .rotateZDegrees(sideways ? 90 : 0)
                .rotateXDegrees(!diagonal && slope != BeltSlope.HORIZONTAL ? 90 : 0)
                .uncenter();
        if (downward || slope == BeltSlope.VERTICAL
                && axisDirection == AxisDirection.POSITIVE) {
            boolean swap = start;
            start = end;
            end = swap;
        }
        float speed = belt == null ? 0.0F : belt.getSpeed();
        float renderTime = belt == null ? 0.0F : AnimationTickHolder.getRenderTime(belt.getLevel());
        for (boolean bottom : Iterate.trueAndFalse) {
            SuperByteBuffer partial = CachedBuffers.partial(
                    BeltRenderer.getBeltPartial(diagonal, start, end, bottom), state).light(light);
            SpriteShiftEntry sprite = BeltRenderer.getSpriteShiftEntry(
                    belt == null ? null : belt.color.orElse(null), diagonal, bottom);
            if (speed != 0.0F || belt != null && belt.color.isPresent()) {
                float appliedSpeed = speed;
                if (diagonal && (downward ^ alongX) || !sideways && !diagonal && alongX
                        || sideways && axisDirection == AxisDirection.NEGATIVE) {
                    appliedSpeed = -appliedSpeed;
                }
                float spriteSize = sprite.getTarget().getV1() - sprite.getTarget().getV0();
                float scrollMultiplier = diagonal ? 3.0F / 8.0F : 0.5F;
                double scroll = appliedSpeed * renderTime * axisDirection.getStep()
                        / (31.5D * 16.0D) + (bottom ? 0.5D : 0.0D);
                scroll = (scroll - Math.floor(scroll)) * spriteSize * scrollMultiplier;
                partial.shiftUVScrolling(sprite, (float) scroll);
            }
            partial.transform(transforms).renderInto(pose, buffers.getBuffer(RenderType.solid()));
            if (diagonal) break;
        }
        if (belt != null && belt.hasPulley()) {
            Direction direction = sideways ? Direction.UP : facing.getClockWise();
            SuperByteBuffer pulley = CachedBuffers.partialDirectional(
                    AllPartialModels.BELT_PULLEY, state, direction, () -> pulleyTransforms(direction));
            KineticBlockEntityRenderer.standardKineticRotationTransform(pulley, belt, light)
                    .renderInto(pose, buffers.getBuffer(RenderType.solid()));
        }
        return true;
    }

    // Create the local pulley transform used by the normal Create belt renderer.
    private static PoseStack pulleyTransforms(Direction direction) {
        PoseStack transforms = new PoseStack();
        var stack = TransformStack.of(transforms);
        stack.center();
        if (direction.getAxis() == Direction.Axis.X) stack.rotateYDegrees(90.0F);
        if (direction.getAxis() == Direction.Axis.Y) stack.rotateXDegrees(90.0F);
        stack.rotateXDegrees(90.0F).uncenter();
        return transforms;
    }
}
