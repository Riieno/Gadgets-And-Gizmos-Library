package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Exercise percentage navigation against different physical ships and actual closed-loop motion
class ScmNavigationSpeedTest{
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);
    private static final double DT = .05D;

    @Test
    void strongerShipsUseTheirAvailableStoppingAuthority(){
        var strong = model(100);
        var weak = model(.4);
        double braking = ScmNavigationSpeed.brakingAcceleration(strong, FORWARD, 2.5);
        assertEquals(100, braking, .001);
        double full = ScmNavigationSpeed.requestedSpeed(1, 1000, 1, .35, braking);
        assertTrue(full > 300, "Strong ships retained the fixed 28 blocks/s ceiling");
        assertEquals(full * .5, ScmNavigationSpeed.requestedSpeed(.5, 1000, 1, .35, braking), 1E-8);
        assertTrue(ScmNavigationSpeed.requestedSpeed(1, 1000, 1, .35,
                ScmNavigationSpeed.brakingAcceleration(weak, FORWARD, 2.5)) < full);
    }

    @Test
    void destinationAndCollisionCapsStillRequireACompleteStop(){
        for(double braking : new double[]{.4, 2.5, 100}){
            double speed = ScmNavigationSpeed.requestedSpeed(1, 40, 1, .35, braking);
            assertTrue(speed * .35 + speed * speed / (2 * braking) <= 39 + 1E-8);
            double collision = ScmSpeedControl.scannedSafeSpeed(8, 100, speed, .25, .35, braking);
            assertTrue(collision <= speed);
            assertTrue(collision * .35 + collision * collision / (2 * braking) <= 7.75 + 1E-8);
        }
        assertEquals(0, ScmNavigationSpeed.requestedSpeed(1, 1, 1, .35, 100));
        assertEquals(0, ScmNavigationSpeed.requestedSpeed(0, 1000, 1, .35, 100));
    }

    @Test
    void collisionScansIncludeTheNextAvailableAccelerationStep(){
        assertEquals(5, ScmNavigationSpeed.scanSpeed(model(100), FORWARD, 0, DT), .001);
        assertEquals(25, ScmNavigationSpeed.scanSpeed(model(100), FORWARD, 20, DT), .001);
        assertEquals(20, ScmNavigationSpeed.scanSpeed(null, FORWARD, 20, DT));
        assertEquals(20, ScmNavigationSpeed.scanSpeed(model(100), FORWARD, 20, Double.NaN));
        assertEquals(2.5, ScmNavigationSpeed.brakingAcceleration(null, FORWARD, 2.5));
        assertEquals(0, ScmNavigationSpeed.requestedSpeed(Double.NaN, 1000, 1, .35, 100));
    }

    @Test
    void fullPowerAcceleratesBeyondTheOldCeilingAndStopsAtTheDestination(){
        var full = flight(1);
        var half = flight(.5);
        assertTrue(full.peak > 100, "The closed-loop ship remained soft capped");
        assertTrue(full.elapsed < 40, "The clear route crawled below available propulsion");
        assertTrue(full.peak > half.peak * 1.5, "Requested speed percentages did not affect actual motion");
        assertTrue(Math.abs(full.position - 2000) <= 1.1 && Math.abs(full.speed) < .15,
                "The full-power ship did not capture its destination safely: " + full);
        assertTrue(Math.abs(half.position - 2000) <= 1.1 && Math.abs(half.speed) < .15,
                "The half-speed ship did not capture its destination safely: " + half);
    }

    private static Flight flight(double fraction){
        var model = model(100);
        var mode = ScmControlModeRegistry.resolve(ScmBuiltinControlModes.AIRSHIP_ID.toString());
        double pos = 0, speed = 0, peak = 0, elapsed = 0;
        for(int idx = 0; idx < 4000; idx++){
            double remaining = 2000 - pos;
            Vec3 direction = new Vec3(0, 0, Math.signum(remaining));
            double braking = ScmNavigationSpeed.brakingAcceleration(model, direction, 2.5);
            double target = ScmNavigationSpeed.requestedSpeed(fraction, Math.abs(remaining), 1, .35, braking);
            var input = new ScmControlMode.ControlInput(new Vec3(0, 0, pos), new Vec3(0, 0, speed), Vec3.ZERO,
                    FORWARD, new Vec3(0, 1, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 2000), direction,
                    Vec3.ZERO, target, 1, .6, false, 10000, 10000, target, fraction,
                    true, false, false, 0, 0, model);
            var output = mode.navigate(input);
            speed += (output.force().z - speed * .04) * DT;
            pos += speed * DT;
            peak = Math.max(peak, speed);
            elapsed += DT;
            if(Math.abs(remaining) <= 1.05 && Math.abs(speed) < .1) break;
        }
        return new Flight(pos, speed, peak, elapsed);
    }

    private static ScmAdaptiveStateModel model(double acceleration){
        return ScmAdaptiveStateModel.sample(List.of(
                new ScmAdaptiveStateModel.Actuator(FORWARD.scale(acceleration), Vec3.ZERO, 0),
                new ScmAdaptiveStateModel.Actuator(FORWARD.scale(-acceleration), Vec3.ZERO, 0)), DT, .25, .01);
    }

    private record Flight(double position, double speed, double peak, double elapsed){}
}
