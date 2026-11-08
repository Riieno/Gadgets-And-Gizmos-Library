package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.navigation.SplineRouteTraversal;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovingReferenceFrameTest{
    @Test
    void translationAndRotationUseTheTrackedPointsVelocity(){
        var frame = new MovingReferenceFrame(new Vec3(10, 2, -4), new Quaterniond().rotateY(Math.PI / 2),
                new Vec3(3, 1, -2), new Vec3(0, 2, 0));
        Vec3 point = frame.origin().add(5, 0, 0);
        near(new Vec3(3, 1, -12), frame.velocityAt(point));
        near(Vec3.ZERO, frame.relativeVelocity(point, new Vec3(3, 1, -12)));
        near(new Vec3(1, -2, 3), frame.toLocalPosition(frame.toWorldPosition(new Vec3(1, -2, 3))));
    }

    @Test
    void commonWorldMotionDoesNotChangeClosureFeedback(){
        Vec3 pos = new Vec3(4, 5, 6), drift = new Vec3(-7, 3, 9), actual = new Vec3(2, -1, 4);
        var fixed = new MovingReferenceFrame(Vec3.ZERO, new Quaterniond(), Vec3.ZERO, Vec3.ZERO);
        var moving = new MovingReferenceFrame(Vec3.ZERO, new Quaterniond(), drift, Vec3.ZERO);
        near(fixed.relativeVelocity(pos, actual), moving.relativeVelocity(pos, actual.add(drift)));
    }

    @Test
    void rotatingFrameSuppliesCentripetalAndCoriolisAcceleration(){
        var frame = new MovingReferenceFrame(Vec3.ZERO, new Quaterniond(), Vec3.ZERO, new Vec3(0, 2, 0));
        near(new Vec3(-12, 0, -8), frame.transportAcceleration(new Vec3(3, 0, 0), new Vec3(2, 0, 0)));
    }

    @Test
    void movingALanePreservesItsCapturedLegAndProgress(){
        var prev = new MovingReferenceFrame(Vec3.ZERO, new Quaterniond(), Vec3.ZERO, Vec3.ZERO);
        var next = new MovingReferenceFrame(new Vec3(12, -3, 7), new Quaterniond().rotateXYZ(.3, .8, -.2),
                new Vec3(2, 0, 0), new Vec3(0, .1, 0));
        var route = new SplineRouteTraversal(WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(0, 0, 10), new Vec3(10, 0, 10))), false);
        route.update(new Vec3(0, 0, 10), 1.25, .5);
        route.update(new Vec3(4, 0, 10), 1.25, .5);
        var moved = route.transformed(pos -> next.movePositionFrom(prev, pos));
        assertTrue(moved.captured());
        assertEquals(route.segment(), moved.segment());
        assertEquals(route.fraction(), moved.fraction(), 1.0E-10);
        near(next.movePositionFrom(prev, route.entry()), moved.entry());
        near(next.moveDirectionFrom(prev, route.forward(.5)), moved.forward(.5));
        var waiting = new SplineRouteTraversal(route.spline(), true).transformed(pos -> next.movePositionFrom(prev, pos));
        assertFalse(waiting.captured());
    }

    @Test
    void invalidNativeSamplesCannotCreateFrames(){
        assertThrows(IllegalArgumentException.class, () -> new MovingReferenceFrame(Vec3.ZERO, new Quaterniond(),
                new Vec3(Double.NaN, 0, 0), Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new MovingReferenceFrame(Vec3.ZERO, new Quaterniond(0, 0, 0, 0),
                Vec3.ZERO, Vec3.ZERO));
        Quaterniond rotation = new Quaterniond().rotateY(.7);
        var frame = new MovingReferenceFrame(Vec3.ZERO, rotation, Vec3.ZERO, Vec3.ZERO);
        rotation.identity();
        frame.orientation().identity();
        assertFalse(frame.samePose(new MovingReferenceFrame(Vec3.ZERO, rotation, Vec3.ZERO, Vec3.ZERO)));
    }

    private static void near(Vec3 expected, Vec3 actual){ assertTrue(expected.distanceTo(actual) < 1.0E-7, expected + " != " + actual); }
}
