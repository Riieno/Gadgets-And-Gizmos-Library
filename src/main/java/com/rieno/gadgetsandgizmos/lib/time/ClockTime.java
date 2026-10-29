package com.rieno.gadgetsandgizmos.lib.time;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Convert game ticks and zoned wall clocks into the same readable time value
public record ClockTime(int hour, int minute, int second){
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    // Reject clock components outside a single day
    public ClockTime{ LocalTime.of(hour, minute, second); }

    // Convert Minecraft's six o'clock day origin without overflowing long tick values
    public static ClockTime world(long dayTime){
        long ticks = (Math.floorMod(dayTime, 24000L) + 6000L) % 24000L;
        return from(LocalTime.ofSecondOfDay(ticks * 86400L / 24000L));
    }

    // Read the chosen zone's civil time using its daylight-saving rules
    public static ClockTime real(Instant instant, String timezone){
        return from(instant.atZone(ZoneId.of(timezone)).toLocalTime());
    }

    // Retain signed offsets for callers of the existing clock API
    public static ClockTime real(Instant instant, String timezone, double offset){
        if(!Double.isFinite(offset) || Math.abs(offset) > 18)
            throw new IllegalArgumentException("Time offsets must be between -18 and 18 hours");
        return from(instant.atZone(ZoneId.of(timezone)).toLocalTime().plusSeconds(Math.round(offset * 3600)));
    }

    // Return the host's configured region so daylight-saving rules remain available
    public static String serverTimezone(){ return ZoneId.systemDefault().getId(); }

    // Format a clock value consistently for graph text and display inputs
    public String text(){ return LocalTime.of(hour, minute, second).format(FORMAT); }

    // Copy the civil time components without retaining dates or mutable state
    private static ClockTime from(LocalTime time){ return new ClockTime(time.getHour(), time.getMinute(), time.getSecond()); }
}
