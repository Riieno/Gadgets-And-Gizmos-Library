package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.lib.kinetics.DirectionalPreciseKineticOutputAccess;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticAngleHelper;
import com.rieno.gadgetsandgizmos.lib.kinetics.HeldAngleKineticGraph;
import com.rieno.gadgetsandgizmos.lib.kinetics.PreciseKineticOutputAccess;
import com.rieno.gadgetsandgizmos.lib.kinetics.PreciseKineticOutputGraph;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;

// Add exact-angle outputs and propagation to Create kinetic block entities
@Mixin(KineticBlockEntity.class)
public abstract class KineticBlockEntityPreciseAngleOutputMixin implements PreciseKineticOutputAccess,
        DirectionalPreciseKineticOutputAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Precise angle output graph
    @Unique
    private final PreciseKineticOutputGraph ct$preciseAngleOutputGraph = new PreciseKineticOutputGraph();

    // Directional precise angle output graph
    @Unique
    private final HeldAngleKineticGraph ct$directionalPreciseAngleOutputGraph = new HeldAngleKineticGraph();

    // Current precise angle sync token
    @Unique
    private int ct$preciseAngleSyncToken;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the precise angle output
    @Override
    public void ct$applyPreciseAngleOutput(float angleDegrees) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (!GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(self)) {
            return;
        }
        Level level = self.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        ct$preciseAngleOutputGraph.apply(
                self,
                KineticAngleHelper.normalizeDegrees(angleDegrees),
                target -> false,
                ct$syncPreciseAngleTarget);
    }

    // Clear the precise angle output
    @Override
    public void ct$clearPreciseAngleOutput() {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        ct$preciseAngleOutputGraph.clear(level, ct$syncPreciseAngleTarget);
    }

    // Apply the directional precise angle output
    @Override
    public void ct$applyDirectionalPreciseAngleOutput(Direction outputFace, float angleDegrees) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide || outputFace == null
                || !GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(self)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(self.getBlockPos().relative(outputFace));
        KineticBlockEntity outputTarget = blockEntity instanceof KineticBlockEntity kinetic ? kinetic : null;
        ct$directionalPreciseAngleOutputGraph.apply(
                level,
                outputTarget,
                KineticAngleHelper.normalizeDegrees(angleDegrees),
                new HashSet<>(),
                target -> target == self,
                ct$syncPreciseAngleTarget);
    }

    // Clear the directional precise angle output
    @Override
    public void ct$clearDirectionalPreciseAngleOutput() {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        ct$directionalPreciseAngleOutputGraph.clear(level, ct$syncPreciseAngleTarget);
    }

    // Clear the precise angle graph on remove
    @Inject(method = "remove", at = @At("HEAD"), remap = false)
    private void ct$clearPreciseAngleGraphOnRemove(CallbackInfo ci) {
        ct$clearPreciseAngleOutput();
        ct$clearDirectionalPreciseAngleOutput();
    }

    // Sync precise angle target
    @Unique
    private final HeldAngleKineticGraph.KineticTargetSynchronizer ct$syncPreciseAngleTarget = target -> {
        target.setChanged();
        target.sendData();
        Level level = target.getLevel();
        if (level != null && ++ct$preciseAngleSyncToken % 4 == 0) {
            level.sendBlockUpdated(target.getBlockPos(), target.getBlockState(), target.getBlockState(), 3);
        }
    };
}
