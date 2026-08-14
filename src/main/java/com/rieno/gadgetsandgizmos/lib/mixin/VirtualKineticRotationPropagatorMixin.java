package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticBlockEntity;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticHostBlock;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticPos;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// Let Create's rotation propagator traverse slot-addressed virtual kinetics
@Mixin(RotationPropagator.class)
public abstract class VirtualKineticRotationPropagatorMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the virtual block entity
    @WrapOperation(method = {"handleRemoved", "propagateMissingSource", "findConnectedNeighbour"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private static BlockEntity ct$resolveVirtualBlockEntity(Level level, BlockPos pos, Operation<BlockEntity> original) {
        return ct$resolveVirtualBlockEntity(original.call(level, pos), pos);
    }

    // Add the virtual kinetic neighbour positions
    @Inject(method = "getPotentialNeighbourLocations", at = @At("TAIL"), remap = false)
    private static void ct$addVirtualKineticNeighbourPositions(KineticBlockEntity be,
                                                               CallbackInfoReturnable<List<BlockPos>> cir) {
        Level level = be.getLevel();
        if (level == null || !GadgetsNGizmosKineticGuard.shouldUseVirtualKineticFor(be, be.getBlockPos())) {
            return;
        }

        List<BlockPos> positions = cir.getReturnValue();
        List<BlockPos> virtualPositions = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (!GadgetsNGizmosKineticGuard.shouldUseVirtualKineticFor(be, pos)) {
                continue;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof VirtualKineticHostBlock hostBlock)
                    || !hostBlock.ct$canExposeVirtualKinetics(level, pos, state)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof VirtualKineticProvider provider)) {
                continue;
            }
            for (int slot = 0; slot < provider.ct$getVirtualKineticCount(); slot++) {
                if (provider.ct$getVirtualKinetic(slot) != null) {
                    virtualPositions.add(new VirtualKineticPos(pos, slot));
                }
            }
        }
        positions.addAll(virtualPositions);
    }

    // Check if the replace from has shaft
    @WrapOperation(method = "getRotationSpeedModifier",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/IRotate;hasShaftTowards(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z", ordinal = 0))
    private static boolean ct$replaceFromHasShaft(IRotate rotate, LevelReader world, BlockPos pos, BlockState state,
                                                  Direction dir, Operation<Boolean> original,
                                                  @Local(argsOnly = true, ordinal = 0) KineticBlockEntity fromBE) {
        return original.call(ct$getRotate(rotate, fromBE), world, pos, state, dir);
    }

    // Check if the replace to has shaft
    @WrapOperation(method = "getRotationSpeedModifier",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/IRotate;hasShaftTowards(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z", ordinal = 1))
    private static boolean ct$replaceToHasShaft(IRotate rotate, LevelReader world, BlockPos pos, BlockState state,
                                                Direction dir, Operation<Boolean> original,
                                                @Local(argsOnly = true, ordinal = 1) KineticBlockEntity toBE) {
        return original.call(ct$getRotate(rotate, toBE), world, pos, state, dir);
    }

    // Replace the first kinetic axis
    @WrapOperation(method = "getRotationSpeedModifier",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/IRotate;getRotationAxis(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/Direction$Axis;", ordinal = 0))
    private static Direction.Axis ct$replaceFromAxis0(IRotate rotate, BlockState state,
                                                      Operation<Direction.Axis> original,
                                                      @Local(argsOnly = true, ordinal = 0) KineticBlockEntity fromBE) {
        return original.call(ct$getRotate(rotate, fromBE), state);
    }

    // Replace the second kinetic axis
    @WrapOperation(method = "getRotationSpeedModifier",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/IRotate;getRotationAxis(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/Direction$Axis;", ordinal = 1))
    private static Direction.Axis ct$replaceFromAxis1(IRotate rotate, BlockState state,
                                                      Operation<Direction.Axis> original,
                                                      @Local(argsOnly = true, ordinal = 0) KineticBlockEntity fromBE) {
        return original.call(ct$getRotate(rotate, fromBE), state);
    }

    // Replace the axis
    @WrapOperation(method = "getRotationSpeedModifier",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/IRotate;getRotationAxis(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/Direction$Axis;", ordinal = 2))
    private static Direction.Axis ct$replaceToAxis(IRotate rotate, BlockState state,
                                                   Operation<Direction.Axis> original,
                                                   @Local(argsOnly = true, ordinal = 1) KineticBlockEntity toBE) {
        return original.call(ct$getRotate(rotate, toBE), state);
    }

    // Resolve the virtual block entity
    @Unique
    private static BlockEntity ct$resolveVirtualBlockEntity(BlockEntity blockEntity, BlockPos pos) {
        if (!(pos instanceof VirtualKineticPos virtualPos) || !(blockEntity instanceof VirtualKineticProvider provider)) {
            return blockEntity;
        }
        if (blockEntity instanceof KineticBlockEntity kineticBlockEntity
                && !GadgetsNGizmosKineticGuard.shouldUseVirtualKineticFor(kineticBlockEntity, pos)) {
            return blockEntity;
        }
        KineticBlockEntity virtual = provider.ct$getVirtualKinetic(virtualPos.ct$getVirtualSlot());
        return virtual != null ? virtual : blockEntity;
    }

    // Get the rotate
    @Unique
    private static IRotate ct$getRotate(IRotate currentRotate, KineticBlockEntity blockEntity) {
        if (blockEntity instanceof VirtualKineticBlockEntity virtual) {
            return virtual.ct$getVirtualKineticRotationConfiguration();
        }
        return currentRotate;
    }
}
