package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Verify ordered progress through long approaches and nearby crossings
class SplineRouteTraversalTest{
    // A twisting vertical approach can advance while moving away from its final connector
    @Test
    void remainingDistanceDecreasesThroughLongVerticalBends(){
        var spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(100, 0, 0), new Vec3(100, 40, 80),
                new Vec3(-40, 60, 80), new Vec3(-40, 80, -40), new Vec3(0, 100, -40), new Vec3(0, 100, 0)));
        var route = new SplineRouteTraversal(spline, true);
        double prev = route.remainingDistance(route.entry());
        double endpointDistance = route.entry().distanceTo(spline.waypoints().getLast());
        boolean movedAway = false;
        for(var pos : spline.sample(0.25D)){
            assertTrue(route.update(pos, 1.25D, 0.5D).found());
            double remaining = route.remainingDistance(pos);
            assertTrue(remaining <= prev + 0.05D, "Ordered progress increased at " + pos);
            movedAway |= pos.distanceTo(spline.waypoints().getLast()) > endpointDistance + 20;
            prev = remaining;
        }
        assertTrue(movedAway, "The fixture never moved away from the endpoint");
        assertTrue(route.finalLeg());
        assertEquals(0, prev, 0.05D);
    }

    // Reaching the entry must retain the unvisited route instead of changing distance metrics
    @Test
    void entryCaptureKeepsRemainingDistanceContinuous(){
        var spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(100, 20, 0), new Vec3(0, 40, 20)));
        var route = new SplineRouteTraversal(spline, true);
        assertEquals(spline.length() + 10, route.remainingDistance(new Vec3(-10, 0, 0)), 1.0E-6D);
        double before = route.remainingDistance(Vec3.ZERO);
        route.update(Vec3.ZERO, 1.25D, 0.5D);
        assertEquals(before, route.remainingDistance(Vec3.ZERO), 0.05D);
    }

    // A later crossing cannot hide the unvisited bends from the watchdog
    @Test
    void remainingDistanceRetainsTheSelectedLegAtCrossings(){
        var spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(40, 0, 0), new Vec3(40, 30, 40),
                new Vec3(0, 30, 40), new Vec3(0, 0, 0), new Vec3(-20, 0, 0)));
        var route = new SplineRouteTraversal(spline, false);
        route.update(Vec3.ZERO, 1.25D, 0.5D);
        assertTrue(route.remainingDistance(Vec3.ZERO) > 150);
        assertEquals(0, route.segment());
        double before = route.remainingDistance(new Vec3(10, 0, 0));
        var restored = SplineRouteTraversal.fromTag(route.toTag()).orElseThrow();
        assertEquals(before, restored.remainingDistance(new Vec3(10, 0, 0)), 1.0E-6D);
        assertTrue(route.remainingDistance(new Vec3(10, -20, 0)) > before);
    }
}
