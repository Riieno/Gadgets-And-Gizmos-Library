package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Keep physical speed limits independent from command percentages and native propulsion
class ScmSpeedLimitTest{
    // Preserve unrestricted movement and the previous percentage reference until a positive limit is set
    @Test void nonpositiveLimitsRemainUnrestricted(){
        for(double maximum : new double[]{0, -10, Double.NaN}){
            var limit = new ScmSpeedLimit(maximum);
            assertFalse(limit.enabled());
            assertEquals(500, limit.cap(500));
            assertEquals(100, limit.percentage(.5D, 200));
            Vec3 acceleration = new Vec3(100, 20, -50);
            assertEquals(acceleration, limit.limitAcceleration(new Vec3(300, 0, 0), acceleration, .05D, .35D));
        }
    }

    // A percentage references the saved maximum while destination braking may still require a lower speed
    @Test void percentagesScaleAgainstThePhysicalMaximum(){
        var limit = new ScmSpeedLimit(20);
        assertEquals(10, limit.percentage(.5D, 400));
        assertEquals(20, limit.cap(400));
        assertEquals(4, limit.cap(4));
        assertEquals(10, ScmNavigationSpeed.requestedSpeed(.5D, 1000, 1, .35D, 100, limit));
        double safe = ScmSpeedControl.stoppingSpeed(1.1D, 1, .35D, 1);
        assertEquals(safe, ScmNavigationSpeed.requestedSpeed(.5D, 1.1D, 1, .35D, 1, limit));
        assertEquals(0, ScmNavigationSpeed.requestedSpeed(0, 1000, 1, .35D, 100, limit));
    }

    // Bound combined forward, lateral and vertical drive without changing an existing stronger brake
    @Test void combinedVelocityCannotAcceleratePastTheCeiling(){
        var limit = new ScmSpeedLimit(5);
        Vec3 velocity = new Vec3(4, 0, 0);
        Vec3 acceleration = limit.limitAcceleration(velocity, new Vec3(100, 50, -100), .05D, .35D);
        assertTrue(velocity.add(acceleration.scale(.05D)).length() <= 5 + 1.0E-9D);
        Vec3 brake = new Vec3(-100, 0, 0);
        assertEquals(brake, limit.limitAcceleration(new Vec3(10, 0, 0), brake, .05D, .35D));
    }

    // Recover from a lowered limit through bounded control rather than changing the body's velocity
    @Test void sustainedManualDriveRespectsUpdatesToTheMaximum(){
        Vec3 velocity = Vec3.ZERO;
        var limit = new ScmSpeedLimit(20);
        for(int tick = 0; tick < 100; tick++){
            Vec3 acceleration = limit.limitAcceleration(velocity, new Vec3(0, 0, 100), .05D, .35D);
            velocity = velocity.add(acceleration.scale(.05D));
            assertTrue(velocity.length() <= 20 + 1.0E-9D);
        }
        limit = new ScmSpeedLimit(5);
        double prev = velocity.length();
        for(int tick = 0; tick < 200; tick++){
            Vec3 acceleration = limit.limitAcceleration(velocity, new Vec3(0, 0, 100), .05D, .35D);
            velocity = velocity.add(acceleration.scale(.05D));
            assertTrue(velocity.length() <= prev + 1.0E-9D);
            prev = velocity.length();
        }
        assertEquals(5, velocity.length(), 1.0E-8D);
    }

    // Keep existing pilot adapters unrestricted unless their caller supplies the new physical ceiling
    @Test void nativePilotCommandsRetainTheOriginalConstructor(){
        assertEquals(0, new ScmPilotControl.Command(Vec3.ZERO, Vec3.ZERO, false, false, false, null).maximumSpeed());
        assertEquals(4, new ScmPilotControl.Command(Vec3.ZERO, Vec3.ZERO, false, false, false, null, 4).maximumSpeed());
        assertFalse(new ScmPilotControl.Navigation(Vec3.ZERO, Vec3.ZERO, 4, 1, false).altitudeOnly());
        assertTrue(new ScmPilotControl.Navigation(new Vec3(0, 20, 0), Vec3.ZERO, 4, 1, false, true).altitudeOnly());
    }
}
