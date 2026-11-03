package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.view.RemoteViewSessions;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
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
    // Leave the server-owned seat in control of its passenger position
    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void lockVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci){
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, player.serverLevel());
        if(RemoteViewSessions.isLocked(player)) ci.cancel();
    }
    // Accept camera block use only through the authenticated lens action
    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void lockBlockUse(ServerboundUseItemOnPacket packet, CallbackInfo ci){
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, player.serverLevel());
        if(RemoteViewSessions.isLocked(player)) ci.cancel();
    }
    // Reject ordinary held-item use while controlling a camera
    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void lockItemUse(ServerboundUseItemPacket packet, CallbackInfo ci){
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, player.serverLevel());
        if(RemoteViewSessions.isLocked(player)) ci.cancel();
    }
    // Reject forged block-breaking packets without suppressing unrelated inventory actions
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void lockBreaking(ServerboundPlayerActionPacket packet, CallbackInfo ci){
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, player.serverLevel());
        if(!RemoteViewSessions.isLocked(player)) return;
        switch(packet.getAction()){
            case START_DESTROY_BLOCK, STOP_DESTROY_BLOCK, ABORT_DESTROY_BLOCK -> ci.cancel();
            default -> {}
        }
    }
}
