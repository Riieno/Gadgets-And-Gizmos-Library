package com.rieno.gadgetsandgizmos.lib.interaction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

// Retain backend player information without keeping a live entity in block data
public record InteractionOrigin(UUID playerId, String playerName, ResourceLocation dimension,
                                 Vec3 position, float yaw, float pitch, long tick){
    // Capture authoritative player information on the owning server thread
    public static InteractionOrigin of(ServerPlayer player){
        return new InteractionOrigin(player.getUUID(), player.getGameProfile().getName(),
                player.level().dimension().location(), player.position(), player.getYRot(), player.getXRot(),
                player.level().getGameTime());
    }
    // Save an interaction for host metadata without exposing it as a graph port
    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Player", playerId);
        tag.putString("Name", playerName);
        tag.putString("Dimension", dimension.toString());
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putLong("Tick", tick);
        return tag;
    }
}
