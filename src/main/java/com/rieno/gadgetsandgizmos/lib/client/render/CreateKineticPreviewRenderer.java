package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Draws Create's Flywheel-owned kinetic partials into an ordinary off-screen
 * buffer. The static block body is rendered separately by the preview.
 *
 * <p>Calling the registered block-entity renderer is insufficient here: its
 * compatibility path deliberately exits while Flywheel owns the level. The
 * old preview fallback instead rotated the complete baked block model, which
 * duplicated housings and never represented multi-part blocks correctly.</p>
 */
final class CreateKineticPreviewRenderer {
    private CreateKineticPreviewRenderer() {
    }

    /** Render only the kinetic geometry that the normal chunk model omits. */
    static boolean render(
            BlockEntity entity,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        if (!(entity instanceof KineticBlockEntity kinetic) || state == null
                || !VisualizationManager.supportsVisualization(kinetic.getLevel())) {
            return false;
        }

        if (CreateBeltPreviewRenderer.render(entity, state, pose, buffers, light)) {
            return true;
        }

        BlockEntityRenderer<?> registered = Minecraft.getInstance()
                .getBlockEntityRenderDispatcher().getRenderer(entity);
        if (renderAeronauticsSimplePropeller(kinetic, state, registered, pose, buffers, light)) {
            return true;
        }
        if (renderAeronauticsBearing(kinetic, state, pose, buffers, light)) {
            return true;
        }
        if (renderCreateBearing(kinetic, state, pose, buffers, light)) {
            return true;
        }

        // Offroad's public compatibility entry point is suppressed while
        // Flywheel owns the source level. Its partial-only implementation is
        // safe to invoke directly and retains the wheel, suspension and shaft.
        if (registered instanceof KineticBlockEntityRenderer<?>
                && classNameContains(kinetic, ".offroad.content.blocks.wheel_mount.")
                && renderRegisteredPartials(registered, kinetic, pose, buffers, light)) {
            return true;
        }

        if (kinetic instanceof GearboxBlockEntity gearbox) {
            renderGearbox(gearbox, state, pose, buffers, light);
            return true;
        }
        if (kinetic instanceof SplitShaftBlockEntity splitShaft) {
            renderSplitShaft(splitShaft, state, pose, buffers, light);
            return true;
        }
        if (kinetic instanceof BracketedKineticBlockEntity bracketed) {
            renderBracketedKinetic(bracketed, state, pose, buffers, light);
            return true;
        }
        if (state.getBlock() instanceof EncasedCogwheelBlock) {
            renderEncasedCog(kinetic, state, ICogWheel.isLargeCog(state),
                    pose, buffers, light);
            return true;
        }
        if (kinetic instanceof MechanicalCrafterBlockEntity crafter) {
            renderCrafterCog(crafter, state, pose, buffers, light);
            return true;
        }
        if (kinetic instanceof MechanicalMixerBlockEntity mixer) {
            renderMixerCog(mixer, state, pose, buffers, light);
            return true;
        }
        if (kinetic instanceof EncasedFanBlockEntity fan) {
            renderEncasedFan(fan, state, pose, buffers, light);
            return true;
        }

        if (registered instanceof KineticBlockEntityRenderer<?>) {
            // Use the registered renderer's own protected model selection.
            // This preserves Create's exact shaft/cog/pump/millstone/etc.
            // partial instead of assuming every IRotate animates its complete
            // block state. The normal render method itself cannot be called:
            // it exits while Flywheel visualizes this level.
            try {
                Object renderedState = invokeRendererMethod(
                        registered, "getRenderedBlockState", kinetic);
                BlockState kineticState = renderedState instanceof BlockState selected
                        ? selected : state;
                Method rotatedModelMethod = rendererMethod(
                        registered, "getRotatedModel", kinetic, kineticState);
                // Create deliberately uses its base model path for plain
                // shafts, cogs, wheels, and several other Flywheel visuals.
                // Rejecting that declaration removes those parts entirely.
                if (rotatedModelMethod == null) return renderGeneric(
                        kinetic, kineticState, pose, buffers, light);
                Object rotatedModel = invokeRendererMethod(rotatedModelMethod, registered,
                        kinetic, kineticState);
                if (!(rotatedModel instanceof SuperByteBuffer partial)) {
                    return false;
                }
                Object selectedType = invokeRendererMethod(
                        registered, "getRenderType", kinetic, kineticState);
                RenderType renderType = selectedType instanceof RenderType type
                        ? type : RenderType.solid();
                KineticBlockEntityRenderer.renderRotatingBuffer(
                        kinetic, partial, pose, buffers.getBuffer(renderType), light);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return renderGeneric(kinetic, state, pose, buffers, light);
            }
        }
        return renderGeneric(kinetic, state, pose, buffers, light);
    }

    /** Render one integration-owned kinetic renderer without its outer compatibility wrapper. */
    private static boolean renderRegisteredPartials(
            BlockEntityRenderer<?> renderer,
            KineticBlockEntity kinetic,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        try {
            Method method = rendererMethod(renderer, "renderSafe", kinetic,
                    AnimationTickHolder.getPartialTicks(kinetic.getLevel()), pose, buffers,
                    light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            if (method == null) return false;
            invokeRendererMethod(method, renderer, kinetic,
                    AnimationTickHolder.getPartialTicks(kinetic.getLevel()), pose, buffers,
                    light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Preserve Create's ordinary rotating block-state path for simple kinetic models. */
    private static boolean renderGeneric(
            KineticBlockEntity kinetic,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        if (!(state.getBlock() instanceof com.simibubi.create.content.kinetics.base.IRotate)) {
            return false;
        }
        KineticBlockEntityRenderer.renderRotatingKineticBlock(
                kinetic, state, pose, buffers.getBuffer(RenderType.cutoutMipped()), light);
        return true;
    }

    /** Draw Create's bearing shaft and animated top rather than treating it as a generic shaft. */
    private static boolean renderCreateBearing(
            KineticBlockEntity kinetic,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        if (!(kinetic instanceof IBearingBlockEntity bearing)
                || !state.hasProperty(BlockStateProperties.FACING)) {
            return false;
        }
        Direction facing = state.getValue(BlockStateProperties.FACING);
        renderBearingShaft(kinetic, state, facing, pose, buffers, light);
        renderBearingTop(kinetic, bearing, state, facing,
                bearing.isWoodenTop() ? AllPartialModels.BEARING_TOP_WOODEN : AllPartialModels.BEARING_TOP,
                pose, buffers, light);
        return true;
    }

    /** Draw Aeronautics' propeller-bearing plate using its own partial, not Create's bearing cap. */
    private static boolean renderAeronauticsBearing(
            KineticBlockEntity kinetic,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        if (!(kinetic instanceof IBearingBlockEntity bearing)
                || !classNameContains(kinetic, ".propeller.bearing.propeller_bearing.")
                || !state.hasProperty(BlockStateProperties.FACING)) {
            return false;
        }
        PartialModel plate = partialModel("dev.eriksonn.aeronautics.index.AeroPartialModels", "BEARING_PLATE");
        if (plate == null) return false;
        Direction facing = state.getValue(BlockStateProperties.FACING);
        renderBearingShaft(kinetic, state, facing, pose, buffers, light);
        renderBearingTop(kinetic, bearing, state, facing, plate, pose, buffers, light);
        return true;
    }

    /** Draw Aeronautics' animated propeller partial; its standard kinetic model is only the shaft. */
    private static boolean renderAeronauticsSimplePropeller(
            KineticBlockEntity kinetic,
            BlockState state,
            BlockEntityRenderer<?> registered,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        if (!classNameContains(kinetic, ".propeller.small.")
                || registered == null || !state.hasProperty(BlockStateProperties.FACING)) {
            return false;
        }
        try {
            Object currentModel = invokeRendererMethod(registered, "getCurrentModel", kinetic);
            if (!(currentModel instanceof PartialModel model)) return false;
            Direction facing = state.getValue(BlockStateProperties.FACING);
            Object renderedAngle = invokeRendererMethod(registered, "getAngle",
                    AnimationTickHolder.getPartialTicks(kinetic.getLevel()), facing, kinetic);
            if (!(renderedAngle instanceof Number number)) return false;

            SuperByteBuffer propeller = CachedBuffers.partialFacing(model, state);
            KineticBlockEntityRenderer.kineticRotationTransform(propeller, kinetic, facing.getAxis(),
                    number.floatValue(), light);
            if (facing.getAxis().isHorizontal()) {
                propeller.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())),
                        Direction.UP);
            }
            if (facing.getAxis().isVertical()) {
                propeller.rotateCentered(AngleHelper.rad(AngleHelper.verticalAngle(facing.getOpposite())),
                        Direction.EAST);
            }
            propeller.translate(0.0F, 0.0F, -0.1875F)
                    .rotateCentered(AngleHelper.rad(-90.0F - AngleHelper.verticalAngle(facing)), Direction.EAST)
                    .renderInto(pose, buffers.getBuffer(RenderType.solid()));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Draw the standard bearing shaft, which is deliberately distinct from the static housing model. */
    private static void renderBearingShaft(
            KineticBlockEntity kinetic,
            BlockState state,
            Direction facing,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        KineticBlockEntityRenderer.renderRotatingBuffer(kinetic,
                CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, facing.getOpposite()),
                pose, buffers.getBuffer(RenderType.solid()), light);
    }

    /** Draw a bearing head with the same orientation and interpolated motion as the source renderer. */
    private static void renderBearingTop(
            KineticBlockEntity kinetic,
            IBearingBlockEntity bearing,
            BlockState state,
            Direction facing,
            PartialModel model,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        float angle = bearing.getInterpolatedAngle(AnimationTickHolder.getPartialTicks(kinetic.getLevel()) - 1.0F);
        SuperByteBuffer top = CachedBuffers.partial(model, state);
        KineticBlockEntityRenderer.kineticRotationTransform(top, kinetic, facing.getAxis(),
                angle / 180.0F * (float) Math.PI, light);
        if (facing.getAxis().isHorizontal()) {
            top.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        }
        top.rotateCentered(AngleHelper.rad(-90.0F - AngleHelper.verticalAngle(facing)), Direction.EAST)
                .renderInto(pose, buffers.getBuffer(RenderType.solid()));
    }

    /** Resolve an optional mod partial without linking the library to that mod at class-load time. */
    private static PartialModel partialModel(String owner, String fieldName) {
        try {
            Field field = Class.forName(owner).getField(fieldName);
            Object value = field.get(null);
            return value instanceof PartialModel partial ? partial : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    /** Match a fully-qualified class-name segment across an optional mod's inheritance tree. */
    private static boolean classNameContains(Object value, String segment) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().contains(segment)) return true;
        }
        return false;
    }

    private static void renderGearbox(
            GearboxBlockEntity gearbox,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        Axis boxAxis = state.getValue(BlockStateProperties.AXIS);
        BlockPos position = gearbox.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(gearbox.getLevel());
        for (Direction direction : Iterate.directions) {
            Axis axis = direction.getAxis();
            if (boxAxis == axis) continue;
            float angle = time * gearbox.getSpeed() * 3.0F / 10.0F;
            if (gearbox.getSpeed() != 0.0F && gearbox.hasSource()) {
                BlockPos source = gearbox.source.subtract(position);
                Direction sourceFacing = Direction.getNearest(
                        source.getX(), source.getY(), source.getZ());
                if (sourceFacing.getAxis() == axis) {
                    angle *= sourceFacing == direction ? 1.0F : -1.0F;
                } else if (sourceFacing.getAxisDirection()
                        == direction.getAxisDirection()) {
                    angle *= -1.0F;
                }
            }
            angle = (angle + KineticBlockEntityRenderer.getRotationOffsetForPosition(
                    gearbox, position, axis)) / 180.0F * (float) Math.PI;
            SuperByteBuffer shaft = CachedBuffers.partialFacing(
                    AllPartialModels.SHAFT_HALF, state, direction);
            KineticBlockEntityRenderer.kineticRotationTransform(
                    shaft, gearbox, axis, angle, light)
                    .renderInto(pose, buffers.getBuffer(RenderType.solid()));
        }
    }

    private static void renderSplitShaft(
            SplitShaftBlockEntity splitShaft,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        Block block = state.getBlock();
        if (!(block instanceof com.simibubi.create.content.kinetics.base.IRotate rotate)) {
            return;
        }
        Axis boxAxis = rotate.getRotationAxis(state);
        BlockPos position = splitShaft.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(splitShaft.getLevel());
        for (Direction direction : Iterate.directions) {
            Axis axis = direction.getAxis();
            if (boxAxis != axis) continue;
            float angle = time * splitShaft.getSpeed() * 3.0F / 10.0F;
            angle *= splitShaft.getRotationSpeedModifier(direction);
            angle = (angle + KineticBlockEntityRenderer.getRotationOffsetForPosition(
                    splitShaft, position, axis)) / 180.0F * (float) Math.PI;
            SuperByteBuffer shaft = CachedBuffers.partialFacing(
                    AllPartialModels.SHAFT_HALF, state, direction);
            KineticBlockEntityRenderer.kineticRotationTransform(
                    shaft, splitShaft, axis, angle, light)
                    .renderInto(pose, buffers.getBuffer(RenderType.solid()));
        }
    }

    private static void renderBracketedKinetic(
            BracketedKineticBlockEntity kinetic,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(kinetic);
        Direction facing = Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE);
        if (ICogWheel.isLargeCog(state)) {
            KineticBlockEntityRenderer.renderRotatingBuffer(
                    kinetic,
                    CachedBuffers.partialFacingVertical(
                            AllPartialModels.SHAFTLESS_LARGE_COGWHEEL, state, facing),
                    pose, buffers.getBuffer(RenderType.solid()), light);
            float shaftAngle = BracketedKineticBlockEntityRenderer
                    .getAngleForLargeCogShaft(kinetic, axis);
            SuperByteBuffer shaft = CachedBuffers.partialFacingVertical(
                    AllPartialModels.COGWHEEL_SHAFT, state, facing);
            KineticBlockEntityRenderer.kineticRotationTransform(
                    shaft, kinetic, axis, shaftAngle, light)
                    .renderInto(pose, buffers.getBuffer(RenderType.solid()));
            return;
        }
        SuperByteBuffer partial = CachedBuffers.partialFacingVertical(
                ICogWheel.isSmallCog(state)
                        ? AllPartialModels.COGWHEEL : AllPartialModels.SHAFT,
                state, facing);
        KineticBlockEntityRenderer.renderRotatingBuffer(
                kinetic, partial, pose, buffers.getBuffer(RenderType.solid()), light);
    }

    private static void renderEncasedCog(
            KineticBlockEntity kinetic,
            BlockState state,
            boolean large,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(kinetic);
        Direction facing = Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE);
        KineticBlockEntityRenderer.renderRotatingBuffer(
                kinetic,
                CachedBuffers.partialFacingVertical(
                        large ? AllPartialModels.SHAFTLESS_LARGE_COGWHEEL
                                : AllPartialModels.SHAFTLESS_COGWHEEL,
                        state, facing),
                pose, buffers.getBuffer(RenderType.solid()), light);
        float angle = large && kinetic instanceof com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity simple
                ? BracketedKineticBlockEntityRenderer.getAngleForLargeCogShaft(simple, axis)
                : KineticBlockEntityRenderer.getAngleForBe(
                        kinetic, kinetic.getBlockPos(), axis);
        if (!(state.getBlock()
                instanceof com.simibubi.create.content.kinetics.base.IRotate rotate)) {
            return;
        }
        for (Direction direction : Iterate.directionsInAxis(axis)) {
            if (!rotate.hasShaftTowards(
                    kinetic.getLevel(), kinetic.getBlockPos(), state, direction)) {
                continue;
            }
            SuperByteBuffer shaft = CachedBuffers.partialFacing(
                    AllPartialModels.SHAFT_HALF, state, direction);
            KineticBlockEntityRenderer.kineticRotationTransform(
                    shaft, kinetic, axis, angle, light)
                    .renderInto(pose, buffers.getBuffer(RenderType.solid()));
        }
    }

    private static void renderCrafterCog(
            MechanicalCrafterBlockEntity crafter,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        SuperByteBuffer cog = CachedBuffers.partial(
                AllPartialModels.SHAFTLESS_COGWHEEL, state);
        KineticBlockEntityRenderer.standardKineticRotationTransform(
                cog, crafter, light);
        cog.rotateCentered(state.getValue(MechanicalCrafterBlock.HORIZONTAL_FACING)
                        .getAxis() != Axis.X ? 0.0F : (float) Math.PI / 2.0F,
                Direction.UP);
        cog.rotateCentered((float) Math.PI / 2.0F, Direction.EAST)
                .renderInto(pose, buffers.getBuffer(RenderType.solid()));
    }

    private static void renderMixerCog(
            MechanicalMixerBlockEntity mixer,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        SuperByteBuffer cog = CachedBuffers.partial(
                AllPartialModels.SHAFTLESS_COGWHEEL, state);
        KineticBlockEntityRenderer.standardKineticRotationTransform(
                cog, mixer, light)
                .renderInto(pose, buffers.getBuffer(RenderType.solid()));
    }

    private static void renderEncasedFan(
            EncasedFanBlockEntity fan,
            BlockState state,
            PoseStack pose,
            MultiBufferSource buffers,
            int light
    ) {
        Direction direction = state.getValue(BlockStateProperties.FACING);
        int lightBehind = LevelRenderer.getLightColor(
                fan.getLevel(), fan.getBlockPos().relative(direction.getOpposite()));
        int lightInFront = LevelRenderer.getLightColor(
                fan.getLevel(), fan.getBlockPos().relative(direction));
        SuperByteBuffer shaft = CachedBuffers.partialFacing(
                AllPartialModels.SHAFT_HALF, state, direction.getOpposite());
        KineticBlockEntityRenderer.standardKineticRotationTransform(
                shaft, fan, lightBehind)
                .renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));

        float speed = fan.getSpeed() * 5.0F;
        if (speed > 0.0F) speed = net.minecraft.util.Mth.clamp(speed, 80.0F, 1280.0F);
        if (speed < 0.0F) speed = net.minecraft.util.Mth.clamp(speed, -1280.0F, -80.0F);
        float angle = AnimationTickHolder.getRenderTime(fan.getLevel())
                * speed * 3.0F / 10.0F % 360.0F;
        SuperByteBuffer inner = CachedBuffers.partialFacing(
                AllPartialModels.ENCASED_FAN_INNER, state, direction.getOpposite());
        KineticBlockEntityRenderer.kineticRotationTransform(
                inner, fan, direction.getAxis(),
                angle / 180.0F * (float) Math.PI, lightInFront)
                .renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
    }

    private static Object invokeRendererMethod(
            Object renderer,
            String name,
            Object... arguments
    ) throws ReflectiveOperationException {
        Method method = rendererMethod(renderer, name, arguments);
        if (method == null) {
            throw new NoSuchMethodException(renderer.getClass().getName() + '#' + name);
        }
        return invokeRendererMethod(method, renderer, arguments);
    }

    private static Object invokeRendererMethod(
            Method method,
            Object renderer,
            Object... arguments
    ) throws ReflectiveOperationException {
        if (!method.trySetAccessible()) {
            throw new IllegalAccessException(
                    "Cannot access Create renderer method " + method.getName());
        }
        return method.invoke(renderer, arguments);
    }

    private static Method rendererMethod(
            Object renderer,
            String name,
            Object... arguments
    ) {
        for (Class<?> type = renderer.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name)
                        || method.getParameterCount() != arguments.length) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean compatible = true;
                for (int index = 0; index < arguments.length; index++) {
                    if (arguments[index] != null
                            && !isParameterCompatible(parameterTypes[index], arguments[index])) {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible) continue;
                return method;
            }
        }
        return null;
    }

    /** Check reflection parameters while retaining Java's primitive-wrapper compatibility. */
    private static boolean isParameterCompatible(Class<?> parameterType, Object argument) {
        if (!parameterType.isPrimitive()) return parameterType.isInstance(argument);
        return parameterType == boolean.class && argument instanceof Boolean
                || parameterType == byte.class && argument instanceof Byte
                || parameterType == char.class && argument instanceof Character
                || parameterType == short.class && argument instanceof Short
                || parameterType == int.class && argument instanceof Integer
                || parameterType == long.class && argument instanceof Long
                || parameterType == float.class && argument instanceof Float
                || parameterType == double.class && argument instanceof Double;
    }
}
