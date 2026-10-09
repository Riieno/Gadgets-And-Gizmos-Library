package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.core.Direction;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Verify attitude and bank signs independently of the controller's mounting axes
class ScmAttitudeTest{
    @Test void everyMountReportsTheSamePitchYawAndSignedBank(){
        for(Direction forward : Direction.values()) for(Direction up : Direction.values()){
            if(!ScmOrientation.isValid(forward, up)) continue;
            var frame = new ScmOrientation(forward, up);
            for(double bank : new double[]{-65, -30, 0, 30, 65}){
                var world = new Quaterniond().rotateY(Math.toRadians(-70))
                        .rotateX(Math.toRadians(20)).rotateZ(Math.toRadians(-bank));
                var body = new Quaterniond(world).mul(frame.rotation().conjugate());
                var attitude = ScmAttitude.measure(body, frame);
                assertEquals(20, attitude.pitch(), 1.0E-8);
                assertEquals(70, attitude.yaw(), 1.0E-8);
                assertEquals(bank, attitude.roll(), 1.0E-8);
            }
        }
    }

    @Test void verticalAndInvertedAttitudesRemainFinite(){
        var frame = new ScmOrientation(Direction.NORTH, Direction.UP);
        for(double pitch : new double[]{-90, -89.999, 0, 89.999, 90, 180}){
            var attitude = ScmAttitude.measure(new Quaterniond().rotateX(Math.toRadians(pitch)), frame);
            assertTrue(Double.isFinite(attitude.pitch()));
            assertTrue(Double.isFinite(attitude.yaw()));
            assertTrue(Double.isFinite(attitude.roll()));
        }
    }
}
