package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.lib.kinetics.PreciseKineticOutputAccess;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep exact kinetic angles through Create steering wheels
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity", remap = false)
public abstract class SteeringWheelPreciseAngleOutputMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the angle
    @Shadow
    public abstract float getAngle();

    // Publish the steering wheel angle
    @Inject(method = "tick", at = @At("TAIL"))
    private void ct$publishSteeringWheelAngle(CallbackInfo ci) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide) {
            return;
        }
        if (!GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(self)) {
            return;
        }
        if (self instanceof PreciseKineticOutputAccess access) {
            access.ct$applyPreciseAngleOutput(this.getAngle());
        }
    }
}
