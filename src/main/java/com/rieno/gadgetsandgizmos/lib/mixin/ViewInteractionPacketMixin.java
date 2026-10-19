package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Server use already ran once; retain custom client packets without sending vanilla use twice
@Mixin(Connection.class)
public abstract class ViewInteractionPacketMixin{
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void replayPacket(Packet<?> packet, PacketSendListener listener, boolean flush, CallbackInfo ci){
        if(ViewInteractionClient.replaying() && (packet instanceof ServerboundUseItemOnPacket || packet instanceof ServerboundUseItemPacket)) ci.cancel();
    }
}
