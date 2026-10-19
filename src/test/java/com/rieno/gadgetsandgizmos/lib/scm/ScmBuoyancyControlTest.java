package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScmBuoyancyControlTest{
    @Test void delayedAltitudeFeedbackSettlesWithNativeHotAirWarmup(){
        for(double strength : new double[]{0.25, 0.5, 1.0}){
            Source source = new Source(200, 100, 9.81);
            source.responseTime = 9;
            var probe = new ScmBuoyancyControl("redstone_block_control", source);
            double height = 1.5;
            double velocity = -0.25;
            for(int tick = 0; tick < 8000; tick++){
                double correction = ScmStateFeedback.delayedRecoveryAcceleration(-height, velocity,
                        strength, 0.05, probe.responseTimeSeconds());
                probe.apply((981 + 100 * correction) / probe.maximumForce());
                // Aeronautics' reference hot-air filling curve: 180 ticks, with faster small changes.
                double error = source.target - source.current;
                double fraction = error / (source.available * 0.05);
                source.current += error / 180 * (1 + 5 / (1 + 3 * fraction * fraction));
                velocity += (source.current / 100 - 1) * 9.81 * 0.05;
                height += velocity * 0.05;
                assertTrue(Math.abs(height) < 5, "Warmup feedback became unstable at strength " + strength);
            }
            assertEquals(0, height, 0.002);
            assertEquals(0, velocity, 0.001);
            assertEquals(100, source.target, 0.001);
        }
    }

    @Test void immediateControlsKeepTheirExistingFeedbackAndZeroStrengthStaysZero(){
        assertEquals(ScmStateFeedback.recoveryAcceleration(2, -1, 0.5, 0.05),
                ScmStateFeedback.delayedRecoveryAcceleration(2, -1, 0.5, 0.05, 0));
        assertEquals(0, ScmStateFeedback.delayedRecoveryAcceleration(2, -1, 0, 0.05, 9));
    }

    @Test void hoveringKeepsWeightSupportAtZeroAltitudeError(){
        Source source = new Source(200, 100, 9.81);
        ScmBuoyancyControl probe = new ScmBuoyancyControl("redstone_block_control", source);
        var units = List.of(new ScmPrecisionAllocator.Unit(
                probe.localEffectDirection().scale(probe.maximumForce()), Vec3.ZERO, false));
        Vec3 weight = new Vec3(0, 981, 0);
        var allocation = ScmPrecisionAllocator.allocate(units, weight, Vec3.ZERO, null);
        probe.apply(allocation.controls()[0]);
        assertEquals(100, source.target, 0.25);
        assertTrue(source.target > 0, "An altitude hold still needs gas at its target height");
        assertEquals(981, probe.read().effect(), 1e-9);
    }

    @Test void downwardCorrectionReducesGasAndUpwardCorrectionIncreasesIt(){
        Source source = new Source(200, 100, 9.81);
        ScmBuoyancyControl probe = new ScmBuoyancyControl("redstone_block_control", source);
        var units = List.of(new ScmPrecisionAllocator.Unit(new Vec3(0, probe.maximumForce(), 0), Vec3.ZERO, false));
        Vec3 weight = new Vec3(0, 981, 0);
        for(double acceleration : new double[]{-2, 0, 2}){
            Vec3 correction = ScmPrecisionAllocator.normalizedAcceleration(units,
                    new Vec3(0, acceleration, 0), 100, weight);
            Vec3 force = ScmPrecisionAllocator.physicalForce(units, correction, weight).add(weight);
            probe.apply(ScmPrecisionAllocator.allocate(units, force, Vec3.ZERO, null).controls()[0]);
            assertEquals((981 + acceleration * 100) / 9.81, source.target, 0.25);
        }
    }

    @Test void sharedEnvelopeCannotMultiplyLiftCapacity(){
        double a = ScmBuoyancyControl.volumeShare(400, 300, 600);
        double b = ScmBuoyancyControl.volumeShare(200, 300, 600);
        assertEquals(200, a);
        assertEquals(100, b);
        assertEquals(300, a + b);
        assertEquals(400, ScmBuoyancyControl.volumeShare(400, 1000, 600));
        assertEquals(0, ScmBuoyancyControl.volumeShare(400, 0, 600));
    }

    @Test void preciseGasTargetsRemainIndependentOfWarmupAndRedstoneSteps(){
        Source source = new Source(1234, 0, 5);
        var probe = new ScmBuoyancyControl("redstone_block_control", source);
        double capacity = probe.maximumForce();
        probe.apply(0.47312);
        assertEquals(1234 * 0.47312, source.target, 1e-9);
        assertEquals(0, probe.read().effect());
        assertEquals(capacity, probe.maximumForce());
        source.available = 600;
        source.forcePerVolume = 2.5;
        assertEquals(1500, probe.maximumForce());
        probe.apply(0.5);
        assertEquals(300, source.target);
        probe.restore();
        assertTrue(source.restored);
    }

    @Test void unavailableAndInvalidInputsCannotStartTheBurner(){
        Source source = new Source(200, 0, 9.81);
        var probe = new ScmBuoyancyControl("redstone_block_control", source);
        probe.apply(Double.NaN);
        assertEquals(0, source.target);
        probe.apply(-1);
        assertEquals(0, source.target);
        probe.apply(2);
        assertEquals(200, source.target);
        source.loaded = false;
        probe.apply(0.5);
        assertEquals(200, source.target);
        assertFalse(probe.isAvailable());
    }

    private static final class Source implements ScmBuoyancyControl.Source{
        double available;
        double current;
        double forcePerVolume;
        double target;
        double responseTime;
        boolean restored;
        boolean loaded = true;
        Source(double available, double current, double forcePerVolume){
            this.available = available;
            this.current = current;
            this.forcePerVolume = forcePerVolume;
        }
        @Override public ScmBuoyancyControl.Sample sample(){
            return new ScmBuoyancyControl.Sample(available, current, forcePerVolume,
                    new Vec3(0, 1, 0), new Vec3(0, 6, 0));
        }
        @Override public void applyVolume(double volume){ target = volume; }
        @Override public void restore(){ restored = true; }
        @Override public boolean isAvailable(){ return loaded; }
        @Override public double responseTimeSeconds(){ return responseTime; }
    }
}
