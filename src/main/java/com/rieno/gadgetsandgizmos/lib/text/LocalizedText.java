package com.rieno.gadgetsandgizmos.lib.text;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

// Resolve client labels and keep components translatable in network snapshots
public final class LocalizedText{
    private LocalizedText() {}

    // Resolve a label when a rendering API requires a string
    public static String get(String key, Object... args){
        return Component.translatable(key, args).getString();
    }

    // Preserve translated validation failures and accept messages from other APIs
    public static Component message(Throwable err){
        if(err instanceof UserMessageException msg) return msg.component();
        return err.getMessage() == null ? Component.empty() : Component.literal(err.getMessage());
    }

    // Store the component without resolving its language on the server
    public static void put(CompoundTag tag, String key, Component text){
        tag.put(key, ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, text).getOrThrow());
    }

    // Read component snapshots while accepting earlier plain text snapshots
    public static Component read(CompoundTag tag, String key){
        if(!tag.contains(key)) return Component.empty();
        if(tag.contains(key, Tag.TAG_STRING)) return Component.literal(tag.getString(key));
        if(!tag.contains(key, Tag.TAG_COMPOUND) && !tag.contains(key, Tag.TAG_LIST)) return Component.empty();
        return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, tag.get(key)).result().orElse(Component.empty());
    }
}
