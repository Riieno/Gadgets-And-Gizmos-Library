package com.rieno.gadgetsandgizmos.lib.time;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphExecutionContext;
import com.rieno.gadgetsandgizmos.lib.graph.GraphHostServices;
import com.rieno.gadgetsandgizmos.lib.graph.GraphServiceKey;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.TimeGraphNode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// Check civil time, game time and graph failure behaviour independently of the wall clock
class ClockTimeTest{
    // Preserve the vanilla six o'clock day origin through noon and midnight
    @Test
    void worldTimeUsesMinecraftDayOrigin(){
        assertEquals("06:00:00", ClockTime.world(0).text());
        assertEquals("12:00:00", ClockTime.world(6000).text());
        assertEquals("00:00:00", ClockTime.world(18000).text());
        assertEquals("05:59:56", ClockTime.world(-1).text());
        assertEquals(ClockTime.world(Math.floorMod(Long.MAX_VALUE, 24000)), ClockTime.world(Long.MAX_VALUE));
    }

    // Apply signed and fractional offsets without losing midnight rollover
    @Test
    void realTimeSupportsUtcGmtAndFractionalOffsets(){
        Instant now = Instant.parse("2026-10-28T23:45:12Z");
        assertEquals("00:45:12", ClockTime.real(now, "UTC", 1).text());
        assertEquals("23:45:12", ClockTime.real(now, "GMT", 0).text());
        assertEquals("05:15:12", ClockTime.real(now, "UTC", 5.5).text());
        assertEquals("22:45:12", ClockTime.real(now, "UTC", -1).text());
    }

    // Follow region daylight-saving rules rather than freezing today's UTC offset
    @Test
    void namedZonesFollowDaylightSavingTransitions(){
        assertEquals("00:30:00", ClockTime.real(Instant.parse("2026-03-29T00:30:00Z"), "Europe/London").text());
        assertEquals("02:30:00", ClockTime.real(Instant.parse("2026-03-29T01:30:00Z"), "Europe/London").text());
        assertEquals("05:45:00", ClockTime.real(Instant.parse("2026-10-28T00:00:00Z"), "Asia/Kathmandu").text());
    }

    // Reject invalid offset values before they can overflow clock arithmetic
    @Test
    void invalidOffsetsAreRejected(){
        assertThrows(IllegalArgumentException.class, () -> ClockTime.real(Instant.EPOCH, "UTC", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ClockTime.real(Instant.EPOCH, "UTC", 19));
    }

    // Default an unspecified real-time zone to the host clock instead of the editor machine
    @Test
    void graphUsesHostClockAndCanSwitchModes(){
        var ctx = context();
        var real = TimeGraphNode.execute(ctx, Map.of("mode", GraphValue.string("Real")));
        assertEquals("05:45:00", real.get("time").asString());
        assertEquals(5, real.get("hour").asNumber());
        assertTrue(real.get("valid").asBoolean());
        assertEquals("12:00:00", TimeGraphNode.execute(ctx, Map.of("mode", GraphValue.string("World"))).get("time").asString());
        assertEquals("01:00:00", TimeGraphNode.execute(ctx, Map.of("mode", GraphValue.string("Real"),
                "timezone", GraphValue.string("Europe/Paris"))).get("time").asString());
    }

    // Ignore retired offsets even when older saved inputs reach the executor
    @Test
    void graphIgnoresLegacyOffsets(){
        var outputs = TimeGraphNode.execute(context(), Map.of("mode", GraphValue.string("Real"),
                "timezone", GraphValue.string("UTC"), "offset", GraphValue.number(Double.NaN)));
        assertTrue(outputs.get("valid").asBoolean());
        assertEquals("00:00:00", outputs.get("time").asString());
    }

    // Keep graph outputs typed and visibly invalid for an unsupported timezone
    @Test
    void invalidZoneDoesNotThrowOrPretendToBeUtc(){
        var outputs = TimeGraphNode.execute(context(), Map.of("mode", GraphValue.string("Real"),
                "timezone", GraphValue.string("Missing/Zone")));
        assertFalse(outputs.get("valid").asBoolean());
        assertEquals("", outputs.get("time").asString());
    }

    // Supply deterministic world and server-zone clocks to the shared executor
    private static GraphExecutionContext context(){
        return new GraphExecutionContext(){
            // Keep graph scheduling separate from the world's solar clock
            @Override public long tick(){ return 500; }
            // Leave state unused for this stateless node
            @Override public GraphValue state(String key){ return GraphValue.number(0); }
            // Ignore writes because the time node owns no retained state
            @Override public void state(String key, GraphValue val){}
            // Supply a server in a region with a quarter-hour offset
            @Override public <T> Optional<T> service(GraphServiceKey<T> key){
                return GraphHostServices.TIME.equals(key) ? Optional.of(key.type().cast(new TimeSource(() -> 6000L,
                        Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneId.of("Asia/Kathmandu"))))) : Optional.empty();
            }
        };
    }
}
