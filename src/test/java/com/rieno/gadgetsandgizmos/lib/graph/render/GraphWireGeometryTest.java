package com.rieno.gadgetsandgizmos.lib.graph.render;

import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry.Bounds;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry.Endpoint;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry.Mode;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry.Path;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Verify socket directions, safe turns, exact wire picking and the eight allowed straight-wire directions
class GraphWireGeometryTest{
    @Test void nearlyAlignedPortsUseOneDiagonalWithoutSmallSteps(){
        for(double x : new double[]{6, 20, 40, 60, 120}){
            for(double y : new double[]{-5, -1, -0.5, 0.5, 1, 5}){
                Bounds source = new Bounds(-160, -60, 0, 60), target = new Bounds(x, y - 60, x + 160, y + 60);
                Path path = GraphWireGeometry.route(new Endpoint(new Point(0, 0), new Point(1, 0), source),
                        new Endpoint(new Point(x, y), new Point(-1, 0), target), List.of(source, target), Mode.STRAIGHT, 8, false);
                assertOctilinear(path);
                assertAvoids(path, List.of(source, target));
                assertMonotone(path, 1, Math.signum(y));
                assertTrue(path.segments().size() <= 3, "A small port offset produced extra steps: " + path.points());
            }
        }
    }

    @Test void steepConnectionsWaitForDiagonalsWithoutDoublingBack(){
        Bounds source = new Bounds(-160, -60, 0, 60), target = new Bounds(40, 60, 200, 180);
        Path path = GraphWireGeometry.route(new Endpoint(new Point(0, 0), new Point(1, 0), source),
                new Endpoint(new Point(40, 120), new Point(-1, 0), target), List.of(source, target), Mode.STRAIGHT, 8, false);
        assertOctilinear(path);
        assertAvoids(path, List.of(source, target));
        assertMonotone(path, 1, 1);
        assertTrue(path.segments().size() <= 5, "A steep wire produced an unnecessary staircase: " + path.points());
    }

    @Test void diagonalRerouteLeadsDoNotOvershootTheOtherPort(){
        Bounds source = new Bounds(-120, -40, 0, 40), target = new Bounds(60, -30, 180, 50);
        Path path = GraphWireGeometry.route(new Endpoint(new Point(0, 0), new Point(1, 1), source),
                new Endpoint(new Point(60, 10), new Point(-1, 0), target), List.of(source, target), Mode.STRAIGHT, 8, false);
        assertOctilinear(path);
        assertAvoids(path, List.of(source, target));
        assertMonotone(path, 1, 1);
        assertTrue(path.segments().size() <= 3, "The reroute lead produced a hook: " + path.points());
    }

    @Test void adjacentSocketsDoNotDoubleBackWhenTheirLeadDistancesOverlap(){
        Bounds source = new Bounds(0, 0, 30, 20), target = new Bounds(40, 0, 70, 20);
        for(Mode mode : Mode.values()){
            Path path = GraphWireGeometry.route(new Endpoint(new Point(30, 10), new Point(1, 0), source),
                    new Endpoint(new Point(40, 10), new Point(-1, 0), target), List.of(source, target), mode, 8);
            assertEquals(10, path.length(), 1.0E-8D);
            if(mode == Mode.STRAIGHT || mode == Mode.ROUNDED) assertEquals(2, path.points().size());
            else assertEquals(1, path.beziers().size());
        }
    }

    @Test void diagonalSocketLeadsBendBeforeTouchingANearbyNode(){
        Bounds source = new Bounds(450, 70, 610, 180), blocker = new Bounds(240, 30, 407, 226);
        Point dir = new Point(Math.sqrt(0.5), -Math.sqrt(0.5));
        Endpoint target = new Endpoint(new Point(365.6, 228.4), dir, new Bounds(338, 222, 372, 256));
        for(Mode mode : Mode.values()){
            Path path = GraphWireGeometry.route(new Endpoint(new Point(610, 140), new Point(1, 0), source), target,
                    List.of(source, blocker, target.owner()), mode, 8);
            assertAvoids(path, List.of(source, blocker));
            if(mode == Mode.STRAIGHT) assertOctilinear(path);
        }
    }

    @Test void straightRoutesOnlyUseAxesAndFortyFiveDegreeDiagonals(){
        for(int x : new int[]{-300, -50, 0, 80, 400}){
            for(int y : new int[]{-220, -30, 0, 60, 350}){
                Path path = GraphWireGeometry.route(new Endpoint(new Point(0, 0), new Point(1, 0)),
                        new Endpoint(new Point(x, y), new Point(-1, 0)), List.of(), Mode.STRAIGHT, 8);
                assertOctilinear(path);
                assertEquals(new Point(0, 0), path.points().getFirst());
                assertEquals(new Point(x, y), path.points().getLast());
            }
        }
        Path diagonal = GraphWireGeometry.route(new Endpoint(new Point(0, 0), new Point(1, 0)),
                new Endpoint(new Point(220, 130), new Point(-1, 0)), List.of(), Mode.STRAIGHT, 8);
        assertTrue(diagonal.segments().stream().anyMatch(part -> Math.abs(part.end().x() - part.start().x()) > 1
                && Math.abs(part.end().y() - part.start().y()) > 1));
    }

    @Test void backwardConnectionsTurnOutsideBothNodeBodies(){
        Bounds source = new Bounds(300, 80, 460, 200), target = new Bounds(0, 80, 160, 200);
        for(Mode mode : Mode.values()){
            Path path = GraphWireGeometry.route(new Endpoint(new Point(460, 140), new Point(1, 0), source),
                    new Endpoint(new Point(0, 140), new Point(-1, 0), target), List.of(source, target), mode, 8);
            assertTrue(path.bounds().right() > source.right());
            assertTrue(path.bounds().left() < target.left());
            assertTrue(path.bounds().top() < 80 || path.bounds().bottom() > 200);
            assertAvoids(path, List.of(source, target));
            assertTrue(path.points().get(1).x() > 460);
            assertTrue(path.points().get(path.points().size() - 2).x() < 0);
            if(mode == Mode.STRAIGHT) assertOctilinear(path);
        }
    }

    @Test void routesAvoidInterveningNodesAndAStaggeredMaze(){
        List<Bounds> blocked = List.of(new Bounds(65, -100, 110, 95), new Bounds(165, -45, 215, 150),
                new Bounds(270, -100, 325, 80), new Bounds(-40, -170, 380, -125), new Bounds(-40, 180, 380, 220));
        for(Mode mode : Mode.values()){
            Path path = GraphWireGeometry.route(new Endpoint(new Point(0, 0), new Point(1, 0)),
                    new Endpoint(new Point(370, 0), new Point(-1, 0)), blocked, mode, 8);
            assertAvoids(path, blocked);
            if(mode == Mode.STRAIGHT) assertOctilinear(path);
        }
    }

    @Test void bezierForwardConnectionsUseCurvesAndSharePickingAndAnimation(){
        Path path = GraphWireGeometry.route(new Endpoint(new Point(0, 0), new Point(1, 0)),
                new Endpoint(new Point(280, 150), new Point(-1, 0)), List.of(), Mode.BEZIER, 8);
        assertTrue(path.points().size() > 16);
        assertEquals(new Point(0, 0), path.pointAt(0));
        assertEquals(new Point(280, 150), path.pointAt(1));
        for(double amount : new double[]{0.1, 0.25, 0.5, 0.9}){
            Point point = path.pointAt(amount);
            assertEquals(0, path.distanceSquared(point.x(), point.y()), 1.0E-8D);
        }
        assertTrue(path.distanceSquared(140, -100) > 100);
        Path diagonal = new Path(List.of(new Point(0, 0), new Point(3, 4)));
        assertEquals(5, diagonal.length(), 1.0E-8D);
        assertEquals(new Point(1.5, 2), diagonal.pointAt(0.5));
    }

    @Test void roundedRoutesKeepLongStraightRunsAndRoundTheirJoins(){
        Endpoint source = new Endpoint(new Point(0, 0), new Point(1, 0));
        Endpoint target = new Endpoint(new Point(280, 150), new Point(-1, 0));
        Path straight = GraphWireGeometry.route(source, target, List.of(), Mode.STRAIGHT, 8);
        Path rounded = GraphWireGeometry.route(source, target, List.of(), Mode.ROUNDED, 8);
        assertEquals(source.position(), rounded.pointAt(0));
        assertEquals(target.position(), rounded.pointAt(1));
        assertTrue(rounded.length() < straight.length());
        assertTrue(rounded.segments().stream().anyMatch(part -> part.length() > 80
                && Math.abs(Math.abs(part.end().x() - part.start().x()) - Math.abs(part.end().y() - part.start().y())) < 1.0E-7D),
                "Smooth wires bowed the long diagonal instead of retaining the straight route");
        for(int idx = 1; idx < rounded.segments().size(); idx++){
            var prev = rounded.segments().get(idx - 1); var next = rounded.segments().get(idx);
            double dot = (prev.end().x() - prev.start().x()) * (next.end().x() - next.start().x())
                    + (prev.end().y() - prev.start().y()) * (next.end().y() - next.start().y());
            assertTrue(dot / (prev.length() * next.length()) > Math.cos(Math.toRadians(10)), "A rounded join retained a hard corner");
        }
        for(double amount : new double[]{.1, .3, .5, .8}){
            var point = rounded.pointAt(amount);
            assertEquals(0, rounded.distanceSquared(point.x(), point.y()), 1.0E-8D);
        }
    }

    @Test void roundedRoutesKeepCloseSocketApproachesOutsideBothOwners(){
        for(double x : new double[]{6, 20, 40, 60, 120}) for(double y : new double[]{-5, -.5, .5, 5}){
            Bounds source = new Bounds(-160, -60, 0, 60), target = new Bounds(x, y - 60, x + 160, y + 60);
            Path path = GraphWireGeometry.route(new Endpoint(new Point(0, 0), new Point(1, 0), source),
                    new Endpoint(new Point(x, y), new Point(-1, 0), target), List.of(source, target), Mode.ROUNDED, 8);
            assertAvoids(path, List.of(source, target));
            assertMonotone(path, 1, Math.signum(y));
        }
    }

    @Test void bezierDetoursUseCompleteCubicSpansWithContinuousTangents(){
        Bounds source = new Bounds(300, 80, 460, 200), target = new Bounds(0, 80, 160, 200);
        Path path = GraphWireGeometry.route(new Endpoint(new Point(460, 140), new Point(1, 0), source),
                new Endpoint(new Point(0, 140), new Point(-1, 0), target), List.of(source, target), Mode.BEZIER, 8);
        assertTrue(path.beziers().size() > 1);
        assertAvoids(path, List.of(source, target));
        boolean bowedSpan = false;
        for(int idx = 0; idx < path.beziers().size(); idx++){
            var curve = path.beziers().get(idx);
            if(idx > 0){
                var prev = path.beziers().get(idx - 1);
                assertEquals(prev.end(), curve.start());
                assertEquals(prev.tangentAt(1).x(), curve.tangentAt(0).x(), 1.0E-7D);
                assertEquals(prev.tangentAt(1).y(), curve.tangentAt(0).y(), 1.0E-7D);
            }
            if(Math.hypot(curve.end().x() - curve.start().x(), curve.end().y() - curve.start().y()) > 200){
                Point mid = curve.pointAt(0.5);
                bowedSpan |= Math.hypot(mid.x() - (curve.start().x() + curve.end().x()) / 2,
                        mid.y() - (curve.start().y() + curve.end().y()) / 2) > 2;
            }
        }
        assertTrue(bowedSpan, "A long detour span was still a straight section with rounded corners");
    }

    @Test void obstacleAvoidanceCanBeDisabledWithoutChangingTheWireStyle(){
        Bounds source = new Bounds(0, 0, 80, 80), target = new Bounds(400, 0, 480, 80), blocker = new Bounds(180, 0, 260, 80);
        Endpoint from = new Endpoint(new Point(80, 40), new Point(1, 0), source), to = new Endpoint(new Point(400, 40), new Point(-1, 0), target);
        for(Mode mode : List.of(Mode.STRAIGHT, Mode.ROUNDED, Mode.BEZIER)){
            Path direct = GraphWireGeometry.route(from, to, List.of(source, blocker, target), mode, 8, false);
            Path routed = GraphWireGeometry.route(from, to, List.of(source, blocker, target), mode, 8, true);
            assertEquals(320, direct.length(), 1.0E-8D);
            assertEquals(0, direct.distanceSquared(220, 40), 1.0E-8D);
            assertAvoids(routed, List.of(source, blocker, target));
            if(mode == Mode.BEZIER){ assertEquals(1, direct.beziers().size()); assertFalse(routed.beziers().isEmpty()); }
        }
        assertEquals(Mode.BEZIER, Mode.fromId("smooth"));
        assertEquals("bezier", Mode.fromId("smooth").id());
        assertEquals(Mode.BEZIER, Mode.fromId("bezier"));
        assertEquals(Mode.ROUNDED, Mode.fromId("rounded"));
        assertEquals("rounded", Mode.ROUNDED.id());
    }

    @Test void reroutesFaceBackwardVerticalAndDiagonalFlowWithoutDependingOnWireMode(){
        Point center = new Point(100, 100);
        assertEquals(new Point(-1, 0), GraphWireGeometry.rerouteDirection(center, List.of(new Point(300, 100)), List.of(new Point(0, 100))));
        assertEquals(new Point(0, 1), GraphWireGeometry.rerouteDirection(center, List.of(new Point(100, 0)), List.of(new Point(100, 300))));
        Point diagonal = GraphWireGeometry.rerouteDirection(center, List.of(new Point(0, 0)), List.of(new Point(300, 300)));
        assertEquals(Math.sqrt(0.5), diagonal.x(), 1.0E-8D); assertEquals(diagonal.x(), diagonal.y(), 1.0E-8D);
        assertEquals(new Point(1, 0), GraphWireGeometry.rerouteDirection(center, List.of(), List.of()));
    }

    @Test void largeGraphsKeepDirectRoutesPractical(){
        List<Bounds> obstacles = new ArrayList<>();
        for(int y = 0; y < 16; y++) for(int x = 0; x < 32; x++) obstacles.add(new Bounds(x * 160, y * 140, x * 160 + 100, y * 140 + 80));
        for(int idx = 0; idx < 32; idx++){
            Path path = GraphWireGeometry.route(new Endpoint(new Point(-60, idx * 70), new Point(1, 0)),
                    new Endpoint(new Point(5100, idx * 70 + 30), new Point(-1, 0)), obstacles, Mode.STRAIGHT, 8);
            assertAvoids(path, obstacles);
            assertOctilinear(path);
        }
    }

    private static void assertOctilinear(Path path){
        for(var part : path.segments()){
            double x = Math.abs(part.end().x() - part.start().x()), y = Math.abs(part.end().y() - part.start().y());
            assertTrue(x < 1.0E-7D || y < 1.0E-7D || Math.abs(x - y) < 1.0E-7D, "A straight wire used an arbitrary diagonal: " + part);
        }
    }

    private static void assertMonotone(Path path, double xSign, double ySign){
        for(var part : path.segments()){
            assertTrue((part.end().x() - part.start().x()) * xSign >= -1.0E-7D, "The wire doubled back horizontally: " + path.points());
            assertTrue((part.end().y() - part.start().y()) * ySign >= -1.0E-7D, "The wire doubled back vertically: " + path.points());
        }
    }

    // Sample each segment densely enough to catch a cut through any of the fixture node bodies
    private static void assertAvoids(Path path, List<Bounds> blocked){
        for(var part : path.segments()){
            int steps = Math.max(2, (int)Math.ceil(part.length() / 2));
            List<Bounds> nearby = blocked.stream().filter(rect -> Math.max(part.start().x(), part.end().x()) > rect.left()
                    && Math.min(part.start().x(), part.end().x()) < rect.right() && Math.max(part.start().y(), part.end().y()) > rect.top()
                    && Math.min(part.start().y(), part.end().y()) < rect.bottom()).toList();
            for(int idx = 1; idx < steps; idx++){
                double t = idx / (double)steps;
                Point point = new Point(part.start().x() + (part.end().x() - part.start().x()) * t,
                        part.start().y() + (part.end().y() - part.start().y()) * t);
                for(Bounds rect : nearby) assertFalse(rect.inflated(-0.01).contains(point), "The wire crossed a node at " + point + " in " + rect);
            }
        }
    }
}
