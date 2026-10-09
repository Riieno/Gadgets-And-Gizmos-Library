package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelConstructionState;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Preserve unfinished bodies whose scheduled blocks have not supplied mass yet
@Mixin(SubLevelContainer.class)
public abstract class SubLevelConstructionRemovalMixin{
    // Defer native invalid-mass removal while construction owns the body
    @WrapOperation(method = "processSubLevelRemovals", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/mass/MassData;isInvalid()Z"))
    private boolean gadgetsngizmos$preserveConstructionMass(MassData data, Operation<Boolean> original, @Local ServerSubLevel body){
        return !SubLevelConstructionState.isBuilding(body) && original.call(data);
    }
}
