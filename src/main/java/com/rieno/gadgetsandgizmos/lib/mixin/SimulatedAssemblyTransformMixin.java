package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Retain Simulated's normal transfer lifecycle when a caller supplies an oriented frame
@Mixin(SimAssemblyHelper.class)
public abstract class SimulatedAssemblyTransformMixin{
    // Replace only the transform belonging to the current library disassembly
    @ModifyExpressionValue(method = "disassembleSubLevel", at = @At(value = "NEW",
            target = "dev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform"), remap = false)
    private static SubLevelAssemblyHelper.AssemblyTransform gadgetsngizmos$orientDisassembly(SubLevelAssemblyHelper.AssemblyTransform transform){
        return SubLevelAssemblyApi.disassemblyTransform(transform);
    }
}
