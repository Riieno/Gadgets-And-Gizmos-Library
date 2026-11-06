package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.entity.HangingEntityMotion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep a server-validated hanging grip from being treated as unsupported flight
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HangingPlayerMovementMixin{
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void keepHeldPlayerValid(CallbackInfo ci){
        if(!HangingEntityMotion.isServerHeld(player)) return;
        clientIsFloating = false;
        aboveGroundTickCount = 0;
    }
}
