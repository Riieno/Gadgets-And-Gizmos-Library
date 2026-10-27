package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.inventory.IndependentFluidContainer;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Independent member storage must not pass through Create's transfer of a single representative fluid
@Mixin(value = ConnectivityHandler.class, remap = false)
public abstract class IndependentFluidConnectivityMixin{
    @Redirect(method = {"tryToFormNewMulti", "tryToFormNewMultiOfWidth", "splitMultiAndInvalidate"},
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/blockEntity/IMultiBlockEntityContainer$Fluid;hasTank()Z"))
    private static boolean gadgetsngizmos$transferOrdinaryTanks(IMultiBlockEntityContainer.Fluid container){
        return !(container instanceof IndependentFluidContainer) && container.hasTank();
    }
}
