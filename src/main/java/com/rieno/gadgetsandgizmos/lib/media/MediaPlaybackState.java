package com.rieno.gadgetsandgizmos.lib.media;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

// Publish playback changes without sending progress packets every tick
public record MediaPlaybackState(ResourceLocation discId, boolean playing, long offsetTicks,
                                 long startedTick, int durationTicks, long revision){
    public static final MediaPlaybackState EMPTY = new MediaPlaybackState(null, false, 0, 0, 0, 0);

    public MediaPlaybackState{
        durationTicks = Math.max(0, durationTicks);
        offsetTicks = Math.clamp(offsetTicks, 0, durationTicks);
        playing = playing && discId != null && durationTicks > 0;
    }

    public long position(long now){ return offsetTicks + (playing ? Math.max(0, now - startedTick) : 0); }
    public long elapsed(long now){ return Math.min(durationTicks, position(now)); }
    public boolean finished(long now){ return playing && position(now) >= durationTicks; }

    public MediaPlaybackState paused(long now){
        return new MediaPlaybackState(discId, false, elapsed(now), now, durationTicks, revision + 1);
    }

    public CompoundTag toTag(){
        var tag = new CompoundTag();
        if(discId != null) tag.putString("Disc", discId.toString());
        tag.putBoolean("Playing", playing);
        tag.putLong("Offset", offsetTicks);
        tag.putLong("Started", startedTick);
        tag.putInt("Duration", durationTicks);
        tag.putLong("Revision", revision);
        return tag;
    }

    public static MediaPlaybackState fromTag(CompoundTag tag){
        return new MediaPlaybackState(ResourceLocation.tryParse(tag.getString("Disc")), tag.getBoolean("Playing"),
                tag.getLong("Offset"), tag.getLong("Started"), tag.getInt("Duration"), tag.getLong("Revision"));
    }
}
