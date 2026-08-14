package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.lib.kinetics.PreciseKineticOutputAccess;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep exact kinetic angles from Create hand cranks
@Mixin(HandCrankBlockEntity.class)
public abstract class HandCrankPreciseAngleOutputMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Publish the hand crank angle
    @Inject(method = "tick", at = @At("TAIL"))
    private void ct$publishHandCrankAngle(CallbackInfo ci) {
        HandCrankBlockEntity self = (HandCrankBlockEntity) (Object) this;
        if (self instanceof ValveHandleBlockEntity) {
            return;
        }
        if (self.getLevel() == null || self.getLevel().isClientSide) {
            return;
        }
        if (!GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(self)) {
            return;
        }
        if (self instanceof PreciseKineticOutputAccess access) {
            access.ct$applyPreciseAngleOutput(self.independentAngle);
        }
    }
}
