package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NavigationMapTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Test void ownShipRemainsCentredAndItsForwardDirectionAlwaysPointsUp(){
        var center = new Vec3(120, 70, -240);
        for(int yaw = 0; yaw < 360; yaw += 15){
            var frame = new NavigationMapProjection(center, yaw, 147, 126, 256);
            var own = frame.project(center);
            assertEquals(73.5, own.x(), 1.0E-8);
            assertEquals(63, own.y(), 1.0E-8);
            double angle = Math.toRadians(yaw);
            var ahead = frame.project(center.add(Math.sin(angle) * 20, 0, -Math.cos(angle) * 20));
            assertEquals(own.x(), ahead.x(), 1.0E-8);
            assertTrue(ahead.y() < own.y());
        }
    }

    @Test void terrainAndMarkersUseTheSameReversibleProjection(){
        var center = new Vec3(-50, 100, 210);
        for(double angle : new double[]{0, 45, 90, 180, -179}){
            var frame = new NavigationMapProjection(center, angle, 147, 126, 256);
            var point = center.add(47, 0, -63);
            var pixel = frame.project(point);
            assertEquals(0, frame.unproject(pixel.x(), pixel.y()).distanceTo(point), 1.0E-8);
        }
    }

    @Test void headingCrossesTheSignedAndCompassSeamsInBothDirections(){
        var heading = new NavigationHeading();
        assertEquals(179, heading.update(179, 0));
        assertEquals(181, heading.update(-179, 1));
        assertEquals(179, heading.update(179, 1));
        heading.reset();
        assertEquals(359, heading.update(359, 0));
        assertEquals(361, heading.update(1, 1));
        assertEquals(359, heading.update(359, 1));
        assertEquals(270, NavigationHeading.normalize(-90));
        assertEquals(0, NavigationHeading.normalize(720));
    }

    @Test void headingRetainsRepeatedTurnsWithoutLargeCorrections(){
        var heading = new NavigationHeading();
        heading.update(0, 0);
        for(int angle = 5; angle <= 1440; angle += 5){
            double raw = (angle + 180) % 360 - 180;
            assertEquals(angle, heading.update(raw, 1), 1.0E-7);
        }
        for(int angle = 1435; angle >= -720; angle -= 5){
            assertEquals(angle, heading.update(angle % 360, 1), 1.0E-7);
        }
    }

    @Test void headingSmoothingUsesTheShortestTurnAndIsIndependentOfFrameRate(){
        var coarse = new NavigationHeading();
        var fine = new NavigationHeading();
        coarse.update(359, 0); fine.update(359, 0);
        double expected = coarse.update(1, .1);
        double actual = 0;
        for(int idx = 0; idx < 10; idx++) actual = fine.update(1, .01);
        assertEquals(expected, actual, 1.0E-7);
        assertTrue(actual > 359 && actual < 361);
        assertEquals(actual, fine.update(Double.NaN, .01));
        assertEquals(actual, fine.update(10, 0));
        assertEquals(actual, fine.update(10, -1));
        var frame = new NavigationMapProjection(Vec3.ZERO, actual, 147, 126, 256);
        var wrapped = new NavigationMapProjection(Vec3.ZERO, NavigationHeading.normalize(actual), 147, 126, 256);
        assertEquals(frame.project(new Vec3(40, 0, -50)), wrapped.project(new Vec3(40, 0, -50)));
    }

    @Test void distantRouteLegsAreClippedAndOffscreenLegsAreRejected(){
        var frame = new NavigationMapProjection(Vec3.ZERO, 90, 147, 126, 256);
        var line = frame.clip(new Vec3(-1_000_000, 0, 0), new Vec3(1_000_000, 0, 0)).orElseThrow();
        assertEquals(73.5, line.from().x(), 1.0E-7);
        assertEquals(73.5, line.to().x(), 1.0E-7);
        assertEquals(126, line.from().y(), 1.0E-7);
        assertEquals(0, line.to().y(), 1.0E-7);
        assertTrue(frame.clip(new Vec3(10000, 0, 10000), new Vec3(11000, 0, 11000)).isEmpty());
        assertTrue(frame.clip(new Vec3(Double.NaN, 0, 0), Vec3.ZERO).isEmpty());
    }

    @Test void retainedSplinesKeepTheirBendsAndMonotonicCursor(){
        var spline = WaypointSpline.of(List.of(new Vec3(0, 0, 0), new Vec3(20, 0, -40), new Vec3(60, 0, -30), new Vec3(80, 0, 0)));
        Vec3 start = spline.segments().get(1).pointAtFraction(.6);
        var route = NavigationMapData.spline(start, spline.waypoints().getLast(), spline, 1, .6);
        assertTrue(route.spline());
        assertEquals(start, route.points().getFirst());
        assertTrue(route.points().size() > 3);
        assertTrue(route.points().size() <= NavigationMapData.MAX_POINTS);
        assertEquals(0, route.points().getLast().distanceTo(route.target()), 1.0E-7);
        for(Vec3 point : route.points()) assertTrue(point.x >= start.x - .01);
    }

    @Test void longRoutesHaveABoundedHorizonWithoutReplacingTheirCurveWithATargetChord(){
        var spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(0, 0, -1000), new Vec3(1000, 0, -1000)));
        var route = NavigationMapData.spline(Vec3.ZERO, spline.waypoints().getLast(), spline, 0, 0);
        assertTrue(route.points().size() <= NavigationMapData.MAX_POINTS);
        assertTrue(route.points().getLast().distanceTo(route.target()) > 500);
        assertEquals(spline.waypoints().getLast(), route.target());
    }

    @Test void directGuidanceAndPlannerPolylinesDoNotInventCurves(){
        var target = new Vec3(80, 0, 0);
        var direct = NavigationMapData.polyline(Vec3.ZERO, target, List.of());
        assertEquals(List.of(Vec3.ZERO, target), direct.points());
        var bend = new Vec3(0, 0, -40);
        var planned = NavigationMapData.polyline(Vec3.ZERO, target, List.of(bend));
        assertEquals(List.of(Vec3.ZERO, bend, target), planned.points());
        assertFalse(planned.spline());
    }

    @Test void groundManeuversKeepTheirRetainedCircularArc(){
        var arc = GroundPathPlanner.Curve.arc(Vec3.ZERO, new Vec3(1, 0, 0), 20, Math.PI / 2, false);
        var route = NavigationMapData.curves(Vec3.ZERO, arc.end(), List.of(arc), 0);
        assertTrue(route.spline());
        assertTrue(route.points().size() > 4);
        for(Vec3 point : route.points()) assertEquals(20, point.distanceTo(arc.center()), 1.0E-6);
    }

    @Test void interpolationConsumesOnlyTheNearPrefixAndDoesNotJumpToALaterCrossing(){
        var target = new Vec3(0, 0, 40);
        var route = new NavigationMapData.Route(target, List.of(Vec3.ZERO, new Vec3(100, 0, 0),
                new Vec3(100, 0, 100), new Vec3(10, 0, 0), target), false);
        var points = new NavigationMapData(List.of(), route).routeFrom(new Vec3(10, 0, 0));
        assertEquals(new Vec3(10, 0, 0), points.get(0));
        assertEquals(new Vec3(10, 0, 0), points.get(1));
        assertEquals(new Vec3(100, 0, 0), points.get(2));
    }

    @Test void transportBoundsRejectInvalidMarkersAndRoundTripDetachedGeometry(){
        var markers = new ArrayList<NavigationMapData.Marker>();
        for(int idx = 0; idx < 100; idx++) markers.add(new NavigationMapData.Marker(UUID.randomUUID(), new Vec3(idx, 0, 0), idx));
        markers.addFirst(new NavigationMapData.Marker(UUID.randomUUID(), new Vec3(Double.NaN, 0, 0), 0));
        var data = new NavigationMapData(markers, NavigationMapData.polyline(Vec3.ZERO, new Vec3(10, 0, 20), List.of()));
        assertEquals(64, data.ships().size());
        var tag = data.toTag();
        assertEquals(64, tag.getList("Ships", Tag.TAG_COMPOUND).size());
        assertEquals(data, NavigationMapData.fromTag(tag));
        assertThrows(UnsupportedOperationException.class, () -> data.ships().clear());
    }
}
