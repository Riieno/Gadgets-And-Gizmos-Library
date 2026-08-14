package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.GadgetsNGizmosKineticGuard;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticAngleHelper;
import com.rieno.gadgetsandgizmos.lib.kinetics.PreciseKineticOutputAccess;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlock;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep exact kinetic angles through Create valve handles
@Mixin(ValveHandleBlockEntity.class)
public abstract class ValveHandlePreciseAngleOutputMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current start angle
    @Shadow
    protected int startAngle;

    // Target angle
    @Shadow
    protected int targetAngle;

    // Total use tick count
    @Shadow
    protected int totalUseTicks;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Publish the valve handle angle
    @Inject(method = "tick", at = @At("TAIL"))
    private void ct$publishValveHandleAngle(CallbackInfo ci) {
        ValveHandleBlockEntity self = (ValveHandleBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide) {
            return;
        }
        if (!GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(self)) {
            return;
        }
        if (self instanceof PreciseKineticOutputAccess access) {
            access.ct$applyPreciseAngleOutput(ct$getServerSafeValveHandleAngle(self));
        }
    }

    // Get the server safe valve handle angle
    @Unique
    private float ct$getServerSafeValveHandleAngle(ValveHandleBlockEntity self) {
        if (self.inUse == 0 && self.source != null && self.getSpeed() != 0.0f) {
            return KineticAngleHelper.getKineticRotationAngleDegrees(self);
        }

        int step = self.getBlockState()
                .getOptionalValue(ValveHandleBlock.FACING)
                .orElse(Direction.SOUTH)
                .getAxisDirection()
                .getStep();
        float angle = this.targetAngle;
        if (self.inUse > 0 && this.totalUseTicks > 0) {
            float progress = Math.min(this.totalUseTicks, this.totalUseTicks - self.inUse) / (float) this.totalUseTicks;
            angle = Mth.lerp(progress, this.startAngle, this.targetAngle);
        }
        return angle * (self.backwards ? -1 : 1) * step;
    }
}
