package com.rieno.gadgetsandgizmos.lib.hologram;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Describe a miniature entity without sending inventories or private entity data
public record HologramEntity(UUID id, ResourceLocation type, Vec3 pos, float yaw, String name, @Nullable UUID subLevelId){
    public HologramEntity(UUID id, ResourceLocation type, Vec3 pos, float yaw, String name){
        this(id, type, pos, yaw, name, null);
    }

    public static HologramEntity of(Entity entity, Vec3 worldPos){
        var body = SableLevelApi.containing(entity);
        if(body == null) body = SableLevelApi.tracking(entity);
        return new HologramEntity(entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                worldPos, entity.getYRot(), entity instanceof net.minecraft.world.entity.player.Player player
                ? player.getGameProfile().getName() : "", body == null ? null : body.getUniqueId());
    }

    public void encode(RegistryFriendlyByteBuf buf){
        buf.writeUUID(id);
        buf.writeResourceLocation(type);
        buf.writeVec3(pos);
        buf.writeFloat(yaw);
        buf.writeUtf(name, 64);
        buf.writeBoolean(subLevelId != null);
        if(subLevelId != null) buf.writeUUID(subLevelId);
    }

    public static HologramEntity decode(RegistryFriendlyByteBuf buf){
        return new HologramEntity(buf.readUUID(), buf.readResourceLocation(), buf.readVec3(), buf.readFloat(), buf.readUtf(64),
                buf.readBoolean() ? buf.readUUID() : null);
    }
}
