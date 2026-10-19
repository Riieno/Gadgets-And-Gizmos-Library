package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EntityMotionTelemetryTest{
    @Test void stationarySamplesStayZeroWithoutAGravityBias(){
        Level level = mock(Level.class);
        var motion = new EntityMotionTelemetry.Motion(level, 10, new Vec3(4, 12, -8));
        for(long tick = 11; tick < 20; tick++){
            motion.advance(level, tick, new Vec3(4, 12, -8));assertEquals(Vec3.ZERO, motion.velocity());
        }
    }

    @Test void measuresEveryAxisInBlocksPerSecondAndStopsImmediately(){
        Level level = mock(Level.class);
        var motion = new EntityMotionTelemetry.Motion(level, 10, new Vec3(4, 12, -8));
        motion.advance(level, 11, new Vec3(4.25, 11.5, -7.25));
        assertEquals(new Vec3(5, -10, 15), motion.velocity());
        motion.advance(level, 12, new Vec3(4.25, 11.5, -7.25));assertEquals(Vec3.ZERO, motion.velocity());
    }

    @Test void repeatReadsInOneTickCannotReplaceTheCompletedVelocity(){
        Level level = mock(Level.class);
        var motion = new EntityMotionTelemetry.Motion(level, 10, Vec3.ZERO);
        motion.advance(level, 11, new Vec3(0, 0.5, 0));
        motion.advance(level, 11, new Vec3(0, 0.5, 0));
        assertEquals(new Vec3(0, 10, 0), motion.velocity());
    }

    @Test void gapsAndWorldChangesStartAFreshBaseline(){
        Level level = mock(Level.class);Level other = mock(Level.class);
        var motion = new EntityMotionTelemetry.Motion(level, 10, Vec3.ZERO);
        motion.advance(level, 15, new Vec3(100, 200, 300));assertEquals(Vec3.ZERO, motion.velocity());
        motion.advance(level, 16, new Vec3(100.5, 200, 300));assertEquals(new Vec3(10, 0, 0), motion.velocity());
        motion.advance(other, 17, Vec3.ZERO);assertEquals(Vec3.ZERO, motion.velocity());
        motion.advance(other, 18, new Vec3(0, 0, -0.5));assertEquals(new Vec3(0, 0, -10), motion.velocity());
    }

    @Test void invalidPositionsClearTheBaselineAndRecoverWithoutSpikes(){
        Level level = mock(Level.class);
        var motion = new EntityMotionTelemetry.Motion(level, 10, Vec3.ZERO);
        motion.advance(level, 11, new Vec3(Double.NaN, 0, 0));assertEquals(Vec3.ZERO, motion.velocity());
        motion.advance(level, 12, new Vec3(100, 0, 0));assertEquals(Vec3.ZERO, motion.velocity());
        motion.advance(level, 13, new Vec3(101, 0, 0));assertEquals(new Vec3(20, 0, 0), motion.velocity());
    }
}
