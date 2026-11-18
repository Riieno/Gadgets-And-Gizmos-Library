package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedDockingApi;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.SimMagnet;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep opted-in connector magnets and their final joint on the same relative roll
@Mixin(value = DockingConnectorPair.class, remap = false)
public abstract class SimulatedDockingAlignmentMixin{
    @Shadow @Final private Quaterniond target;

    @Inject(method = "getRelativeOrientationDifference", at = @At("RETURN"), cancellable = true)
    private void gadgetsngizmos$steppedAlignment(SimMagnet first, SimMagnet second, Vector3d error, CallbackInfoReturnable<Vector3d> cir){
        if(first instanceof BlockEntity endpoint1 && second instanceof BlockEntity endpoint2
                && SimulatedDockingApi.resolveAlignment(endpoint1, endpoint2, target, error)) cir.setReturnValue(error);
    }
}
