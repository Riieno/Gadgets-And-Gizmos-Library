package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.lib.kinetics.PreciseKineticOutputAccess;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep exact kinetic angles through Create sequenced gearshifts
@Mixin(SequencedGearshiftBlockEntity.class)
public abstract class SequencedGearshiftPreciseAngleOutputMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current instruction progress
    @Shadow
    float currentInstructionProgress;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is idle
    @Shadow
    public abstract boolean isIdle();

    // Publish the sequenced gearshift angle
    @Inject(method = "tick", at = @At("TAIL"))
    private void ct$publishSequencedGearshiftAngle(CallbackInfo ci) {
        SequencedGearshiftBlockEntity self = (SequencedGearshiftBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide) {
            return;
        }
        if (self.sequenceContext == null || self.sequenceContext.instruction() != SequencerInstructions.TURN_ANGLE || this.isIdle()) {
            return;
        }
        if (!GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(self)) {
            return;
        }
        if (self instanceof PreciseKineticOutputAccess access) {
            access.ct$applyPreciseAngleOutput(this.currentInstructionProgress);
        }
    }
}
