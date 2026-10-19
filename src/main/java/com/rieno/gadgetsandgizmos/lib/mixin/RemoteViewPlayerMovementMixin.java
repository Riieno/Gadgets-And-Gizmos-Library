package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.view.RemoteViewSessions;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reject body motion while server-owned camera control is active
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RemoteViewPlayerMovementMixin{
    @Shadow public ServerPlayer player;
    // Preserve the saved body position and rotation
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void lockMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci){
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, player.serverLevel());
        if(RemoteViewSessions.isLocked(player)) ci.cancel();
    }
    // Suppress riding and directional body input
    @Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true)
    private void lockInput(ServerboundPlayerInputPacket packet, CallbackInfo ci){
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, player.serverLevel());
        if(RemoteViewSessions.isLocked(player)) ci.cancel();
    }
}
