package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.nbt.CompoundTag;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

// Resolve clock cards from world time or an explicit real-world timezone
public record ClockCardSettings(boolean digital, boolean realTime, String timezone){
    public static final List<String> TIMEZONES = ZoneId.getAvailableZoneIds().stream().sorted().toList();
    public static final ClockCardSettings DEFAULT = new ClockCardSettings(false, false, "UTC");

    public ClockCardSettings{
        if(timezone == null || !TIMEZONES.contains(timezone)) timezone = "UTC";
    }

    public LocalTime time(long dayTime, Instant now){
        if(realTime) return now.atZone(ZoneId.of(timezone)).toLocalTime();
        long ticks = Math.floorMod(dayTime, 24000);
        return LocalTime.ofSecondOfDay(Math.floorMod((ticks + 6000) * 3600 / 1000, 86400));
    }

    public CompoundTag toTag(){
        var tag = new CompoundTag();
        tag.putBoolean("Digital", digital); tag.putBoolean("RealTime", realTime); tag.putString("Timezone", timezone);
        return tag;
    }
    public static ClockCardSettings fromTag(CompoundTag tag){
        return new ClockCardSettings(tag.getBoolean("Digital"), tag.getBoolean("RealTime"), tag.getString("Timezone"));
    }
}
