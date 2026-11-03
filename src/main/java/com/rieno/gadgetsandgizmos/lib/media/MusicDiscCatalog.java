package com.rieno.gadgetsandgizmos.lib.media;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.function.ToIntFunction;

// Resolve registered jukebox items through the current world's song registry
public final class MusicDiscCatalog{
    private MusicDiscCatalog(){}

    public record Track(ResourceLocation itemId, SoundEvent sound, Component title, int durationTicks){}

    public static Track resolve(HolderLookup.Provider registries, ResourceLocation itemId){
        if(itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return null;
        var song = JukeboxSong.fromStack(registries, new ItemStack(BuiltInRegistries.ITEM.get(itemId)));
        if(song.isEmpty()) return null;
        var val = song.get().value();
        return new Track(itemId, val.soundEvent().value(), val.description(), Math.max(1, val.lengthInTicks()));
    }

    public static List<Track> tracks(HolderLookup.Provider registries){
        return BuiltInRegistries.ITEM.keySet().stream().map(id -> resolve(registries, id))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing((Track track) -> track.title().getString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(track -> track.itemId().toString())).toList();
    }

    // Cache registry lookups for one playlist operation without retaining a world globally
    public static ToIntFunction<ResourceLocation> lengths(HolderLookup.Provider registries){
        var cache = new HashMap<ResourceLocation, Integer>();
        return id -> id == null ? 0 : cache.computeIfAbsent(id, key -> {
            var track = resolve(registries, key);
            return track == null ? 0 : track.durationTicks();
        });
    }
}
