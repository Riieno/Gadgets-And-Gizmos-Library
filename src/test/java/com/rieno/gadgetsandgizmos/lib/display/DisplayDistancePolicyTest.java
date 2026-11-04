package com.rieno.gadgetsandgizmos.lib.display;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplayDistancePolicyTest{
    @Test void twoChunksUpdateFourChunksCull(){
        var policy = new DisplayDistancePolicy(32, 64);
        assertEquals(DisplayDistancePolicy.Mode.LIVE, policy.mode(32 * 32, true));
        assertEquals(DisplayDistancePolicy.Mode.STATIC, policy.mode(32 * 32 + .01, true));
        assertEquals(DisplayDistancePolicy.Mode.STATIC, policy.mode(64 * 64, true));
        assertEquals(DisplayDistancePolicy.Mode.HIDDEN, policy.mode(64 * 64 + .01, true));
        assertEquals(DisplayDistancePolicy.Mode.HIDDEN, policy.mode(0, false));
        assertEquals(DisplayDistancePolicy.Mode.HIDDEN, policy.mode(Double.NaN, true));
        assertThrows(IllegalArgumentException.class, () -> new DisplayDistancePolicy(64, 32));
    }
    @Test void staticPresentationsCannotCancelLiveDemand(){
        var demand = new DisplayFrameDemand();
        assertFalse(demand.live(0));
        demand.request(10, true); demand.request(10, false);
        assertTrue(demand.live(10)); assertTrue(demand.live(11)); assertFalse(demand.live(12));
        demand.request(11, false); assertFalse(demand.live(11));
        demand.request(11, true); assertTrue(demand.live(11));
    }
}
