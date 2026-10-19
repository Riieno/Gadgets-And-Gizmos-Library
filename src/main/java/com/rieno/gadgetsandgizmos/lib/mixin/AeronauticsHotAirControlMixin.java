package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.control.LiftingGasControl;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity", remap = false)
public abstract class AeronauticsHotAirControlMixin implements LiftingGasControl{
    @Shadow protected int signalStrength;
    @Shadow protected ScrollValueBehaviour hotAirAmountBehaviour;
    @Shadow public abstract void updateSignal();
    @Unique private volatile double gadgetsngizmos$gasVolume = Double.NaN;

    @Override public void setControlledGasVolume(double volume){
        BlockEntity self = (BlockEntity)(Object)this;
        if(self.getLevel() == null || self.getLevel().isClientSide || self.isRemoved()) return;
        double maximum = hotAirAmountBehaviour == null ? 0.0D : Math.max(0.0D, hotAirAmountBehaviour.value);
        gadgetsngizmos$gasVolume = Double.isFinite(volume) ? Math.clamp(volume, 0.0D, maximum) : 0.0D;
        signalStrength = maximum <= 0.0D ? 0 : (int)Math.ceil(15.0D * gadgetsngizmos$gasVolume / maximum);
    }

    @Override public void clearControlledGasVolume(){
        gadgetsngizmos$gasVolume = Double.NaN;
        BlockEntity self = (BlockEntity)(Object)this;
        if(self.getLevel() != null && !self.getLevel().isClientSide && !self.isRemoved()) updateSignal();
    }

    // Keep the native gas simulation and replace only its exact target volume
    @Inject(method = "getGasOutput", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$controlledVolume(CallbackInfoReturnable<Double> cir){
        if(Double.isFinite(gadgetsngizmos$gasVolume)) cir.setReturnValue(gadgetsngizmos$gasVolume);
    }

    @Inject(method = "updateSignal", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$retainControl(CallbackInfo ci){
        if(Double.isFinite(gadgetsngizmos$gasVolume)) ci.cancel();
    }

    // Transmit the exact value for goggles without persisting a control owner across reloads
    @Inject(method = "write", at = @At("TAIL"))
    private void gadgetsngizmos$writeControl(CompoundTag tag, HolderLookup.Provider registries,
            boolean clientPacket, CallbackInfo ci){
        if(clientPacket && Double.isFinite(gadgetsngizmos$gasVolume))
            tag.putDouble("GGLControlledGasVolume", gadgetsngizmos$gasVolume);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void gadgetsngizmos$readControl(CompoundTag tag, HolderLookup.Provider registries,
            boolean clientPacket, CallbackInfo ci){
        gadgetsngizmos$gasVolume = clientPacket && tag.contains("GGLControlledGasVolume")
                ? tag.getDouble("GGLControlledGasVolume") : Double.NaN;
    }
}
