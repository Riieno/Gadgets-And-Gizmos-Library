package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.power.powergrid.PowerGridDeviceConnectorBehaviour;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.BridgeElectricBehaviour;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Extend placed Device Connectors while preserving their native behaviour on other blocks
@Mixin(value = DeviceConnectorBlockEntity.class, remap = false)
public abstract class PowerGridDeviceConnectorMixin{
    @Shadow protected ElectricWire converterWire;

    @Inject(method = "makeBridge", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$makeBridge(CallbackInfoReturnable<BridgeElectricBehaviour> cir){
        cir.setReturnValue(new PowerGridDeviceConnectorBehaviour((DeviceConnectorBlockEntity)(Object)this, () -> converterWire));
    }

    @Inject(method = "buildCircuit", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$sharedCircuit(IElectricEntity.CircuitBuilder builder, CallbackInfo ci){
        var target = (DeviceConnectorBlockEntity)(Object)this;
        if(target.getBehaviour(ElectricBehaviour.TYPE) instanceof PowerGridDeviceConnectorBehaviour bridge && bridge.shared()){
            bridge.buildCircuit(builder);
            ci.cancel();
        }
    }
}
