package com.rieno.gadgetsandgizmos.lib.kinetics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KineticTargetSpeedTest{
    @Test
    void percentagesScaleEitherRotationDirectionWithoutAmplifyingIt(){
        for(float input : new float[]{128.0f, -128.0f}){
            assertEquals(input / 2.0f, input * KineticTargetSpeed.percentageRatio(50.0));
            assertEquals(0.0f, input * KineticTargetSpeed.percentageRatio(0.0), 0.0f);
            assertEquals(input, input * KineticTargetSpeed.percentageRatio(150.0));
        }
        assertEquals(0.0f, KineticTargetSpeed.percentageRatio(-50.0));
        assertEquals(0.0f, KineticTargetSpeed.percentageRatio(Double.NaN));
        assertEquals(0.0f, KineticTargetSpeed.percentageRatio(Double.POSITIVE_INFINITY));
        assertEquals(0.25f, KineticTargetSpeed.outputRatio(128, 64) * KineticTargetSpeed.percentageRatio(50));
    }
}
