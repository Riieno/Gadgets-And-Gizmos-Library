package com.rieno.gadgetsandgizmos.lib.kinetics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticSoundTimingTest {
    @Test
    void fitsRatchetTimingSamples(){
        assertEquals(160, KineticSoundTiming.fittedReciprocalDelayTicks(1.0f,
                1.0f, 8.0f, 5.0f, 2.5f));
        assertEquals(50, KineticSoundTiming.fittedReciprocalDelayTicks(5.0f,
                1.0f, 8.0f, 5.0f, 2.5f));
        assertEquals(80, KineticSoundTiming.fittedReciprocalDelayTicks(1.0f,
                1.0f, 4.0f, 5.0f, 1.2f));
        assertEquals(24, KineticSoundTiming.fittedReciprocalDelayTicks(5.0f,
                1.0f, 4.0f, 5.0f, 1.2f));
    }

    @Test
    void decreasesAcrossTheKineticSpeedRange(){
        int prev = Integer.MAX_VALUE;
        for(int rpm = 1; rpm <= 256; rpm++){
            int delay = KineticSoundTiming.fittedReciprocalDelayTicks(rpm,
                    1.0f, 8.0f, 5.0f, 2.5f);
            assertTrue(delay <= prev);
            prev = delay;
        }
    }
}
