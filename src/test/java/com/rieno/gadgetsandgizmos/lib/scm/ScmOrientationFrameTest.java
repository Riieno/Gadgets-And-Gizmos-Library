package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verify all signed craft frames, including vertical forward and inverted up
class ScmOrientationFrameTest{
    @Test
    void everySignedFrameMapsCanonicalAxesAndRoundTripsVectors(){
        int count = 0;
        Vec3 val = new Vec3(2, -3, 4);
        for(Direction forward : Direction.values()){
            for(Direction up : Direction.values()){
                if(!ScmOrientation.isValid(forward, up)) continue;
                count++;
                var frame = new ScmOrientation(forward, up);
                assertEquals(frame.forwardVector(), frame.toBody(new Vec3(0, 0, -1)));
                assertEquals(frame.upVector(), frame.toBody(new Vec3(0, 1, 0)));
                assertEquals(val, frame.fromBody(frame.toBody(val)));
                var rotated = frame.rotation().transform(new Vector3d(0, 0, -1));
                assertEquals(0, frame.forwardVector().distanceTo(new Vec3(rotated.x, rotated.y, rotated.z)), 1.0E-9);
            }
        }
        assertEquals(24, count);
    }

    @Test
    void everySignedFramePreservesTensorResponseIncludingCrossAxisTerms(){
        Matrix3d inertia = new Matrix3d(5, 1, 2, 1, 7, 3, 2, 3, 11);
        Vec3 rate = new Vec3(2, -3, 4);
        for(Direction forward : Direction.values()){
            for(Direction up : Direction.values()){
                if(!ScmOrientation.isValid(forward, up)) continue;
                var frame = new ScmOrientation(forward, up);
                Vec3 physicalRate = frame.toBody(rate);
                var momentum = inertia.transform(new Vector3d(physicalRate.x, physicalRate.y, physicalRate.z));
                Vec3 expected = frame.fromBody(new Vec3(momentum.x, momentum.y, momentum.z));
                var actual = frame.fromBodyTensor(inertia).transform(new Vector3d(rate.x, rate.y, rate.z));
                assertEquals(0, expected.distanceTo(new Vec3(actual.x, actual.y, actual.z)), 1.0E-9);
            }
        }
    }
}
