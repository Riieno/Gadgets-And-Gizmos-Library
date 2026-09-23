package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.KineticConnectionFilter;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Let kinetic block entities filter individual physical connections
@Mixin(RotationPropagator.class)
public abstract class KineticConnectionFilterRotationPropagatorMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Filter the resolved kinetic connection
    @Inject(method = "getRotationSpeedModifier", at = @At("RETURN"), cancellable = true)
    private static void ct$filterKineticConnection(KineticBlockEntity from, KineticBlockEntity to,
                                                   CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() == 0.0f) return;
        if (!ct$allowsConnection(from, to)) cir.setReturnValue(0.0f);
    }

    // Filter special conveyed-speed connections
    @Inject(method = "getConveyedSpeed", at = @At("RETURN"), cancellable = true)
    private static void ct$filterConveyedSpeed(KineticBlockEntity from, KineticBlockEntity to,
                                               CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() == 0.0f) return;
        if (!ct$allowsConnection(from, to)) cir.setReturnValue(0.0f);
    }

    // Filter special connection checks
    @Inject(method = "isConnected", at = @At("RETURN"), cancellable = true)
    private static void ct$filterConnected(KineticBlockEntity from, KineticBlockEntity to,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (!ct$allowsConnection(from, to)) cir.setReturnValue(false);
    }

    // Check both endpoints of one directed connection
    private static boolean ct$allowsConnection(KineticBlockEntity from, KineticBlockEntity to) {
        if (from instanceof KineticConnectionFilter filter
                && !filter.allowsKineticConnection(to, true)) return false;
        return !(to instanceof KineticConnectionFilter filter)
                || filter.allowsKineticConnection(from, false);
    }
}
