package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

class ClockCardSettingsTest{
    @Test void gameClocksFollowWorldDayTimeAndWrapAtMidnight(){
        var clock = ClockCardSettings.DEFAULT;
        assertEquals(LocalTime.of(6, 0), clock.time(0, Instant.EPOCH));
        assertEquals(LocalTime.of(12, 0), clock.time(6000, Instant.EPOCH));
        assertEquals(LocalTime.MIDNIGHT, clock.time(18000, Instant.EPOCH));
        assertEquals(clock.time(0, Instant.EPOCH), clock.time(24000, Instant.EPOCH));
        assertEquals(clock.time(23999, Instant.EPOCH), clock.time(-1, Instant.EPOCH));
    }

    @Test void realClocksUseTheirOwnTimezoneAndDaylightSavingRules(){
        var london = new ClockCardSettings(true, true, "Europe/London");
        var tokyo = new ClockCardSettings(false, true, "Asia/Tokyo");
        assertEquals(LocalTime.of(13, 0), london.time(0, Instant.parse("2026-07-01T12:00:00Z")));
        assertEquals(LocalTime.of(12, 0), london.time(0, Instant.parse("2026-01-01T12:00:00Z")));
        assertEquals(LocalTime.of(21, 0), tokyo.time(18000, Instant.parse("2026-07-01T12:00:00Z")));
    }

    @Test void clockOptionsRoundTripAlongsideExistingCardAppearance(){
        var clock = new ClockCardSettings(true, true, "Europe/London");
        var style = new ControlCardStyle(ControlCardStyle.Size.WIDE, 0x336677, ResourceLocation.parse("minecraft:clock"), clock);
        assertEquals(style, ControlCardStyle.fromTag(style.toTag()));
        assertEquals(ClockCardSettings.DEFAULT, ControlCardStyle.fromTag(new CompoundTag()).clock());
        assertEquals("UTC", new ClockCardSettings(false, true, "not/a/zone").timezone());
    }
}
