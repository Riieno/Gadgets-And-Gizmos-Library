package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Compare optimized proximity decisions with the original exhaustive geometry and escape selector
class ProximitySelectorOptimizationTest{
    private static final AABB HULL = new AABB(-1, -1, -1, 1, 1, 1);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Test void broadPhaseMatchesTheExhaustiveReference(){
        Random random = new Random(713);
        List<AABB> hulls = List.of(HULL, HULL.move(3, 1, 0), HULL.move(-2, -1, 1));
        for(int idx = 0; idx < 1000; idx++){
            Vec3 velocity = vector(random, 4);
            List<ReactiveCollisionAvoidance.MovingObstacle> obstacles = new ArrayList<>();
            for(int other = 0; other < 16; other++) obstacles.add(new ReactiveCollisionAvoidance.MovingObstacle(
                    HULL.move(vector(random, 20)), vector(random, 6)));
            double horizon = random.nextDouble() * 2;
            var expected = reference(hulls, velocity, obstacles, 1, horizon);
            var actual = ReactiveCollisionAvoidance.proximityHazard(hulls, velocity, obstacles, 1, horizon);
            assertEquals(expected.available(), actual.available());
            assertEquals(expected.clearance(), actual.clearance(), 1.0E-10D);
            assertEquals(expected.requiredClearance(), actual.requiredClearance(), 1.0E-10D);
            assertTrue(!actual.available() || expected.direction().dot(actual.direction()) > 0.999999D);
        }
    }

    @Test void clearOutwardMotionNeedsOnlyOneProbe(){
        var hazard = new ReactiveCollisionAvoidance.ProximityHazard(new Vec3(1, 0, 0), 0.5D, 2);
        List<Vec3> directions = List.of(new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(0, 0, 1));
        AtomicInteger probes = new AtomicInteger();
        var escape = ReactiveCollisionAvoidance.resolveProximity(hazard, directions, direction -> {
            probes.incrementAndGet(); return new ReactiveCollisionAvoidance.EscapeCandidate(direction, 8);
        });
        var full = ReactiveCollisionAvoidance.resolveProximity(hazard,
                directions.stream().map(direction -> new ReactiveCollisionAvoidance.EscapeCandidate(direction, 8)).toList());
        assertEquals(full, escape.response());
        assertEquals(8, escape.clearance());
        assertEquals(1, probes.get());
    }

    @Test void obstructedDepartureStillProbesOverAndUnderAlternatives(){
        for(int sign : new int[]{-1, 1}){
            var hazard = new ReactiveCollisionAvoidance.ProximityHazard(new Vec3(1, 0, 0), 0.5D, 2);
            List<Vec3> directions = List.of(new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, -1, 0));
            AtomicInteger probes = new AtomicInteger();
            var escape = ReactiveCollisionAvoidance.resolveProximity(hazard, directions, direction -> {
                probes.incrementAndGet(); return new ReactiveCollisionAvoidance.EscapeCandidate(direction, direction.y == sign ? 8 : 0);
            });
            assertEquals(new Vec3(0, sign, 0), escape.response().escapeDirection());
            assertEquals(3, probes.get());
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static ReactiveCollisionAvoidance.ProximityHazard reference(List<AABB> hulls, Vec3 velocity,
            List<ReactiveCollisionAvoidance.MovingObstacle> obstacles, double margin, double horizon){
        var res = ReactiveCollisionAvoidance.ProximityHazard.NONE;
        double risk = Double.POSITIVE_INFINITY;
        for(AABB hull : hulls) for(var obstacle : obstacles){
            AABB other = obstacle.bounds();
            Vec3 offset = new Vec3(gap(hull.minX, hull.maxX, other.minX, other.maxX),
                    gap(hull.minY, hull.maxY, other.minY, other.maxY), gap(hull.minZ, hull.maxZ, other.minZ, other.maxZ));
            Vec3 fallback = other.getCenter().subtract(hull.getCenter());
            fallback = fallback.lengthSqr() > 1.0E-12D ? fallback.normalize() : new Vec3(1, 0, 0);
            Vec3 direction = offset.lengthSqr() > 1.0E-12D ? offset.normalize() : fallback;
            double distance = offset.length();
            Vec3 relative = obstacle.velocity().subtract(velocity);
            double required = margin + Math.max(0, -relative.dot(direction)) * horizon;
            if(distance >= required) continue;
            AABB corridor = hull.inflate(margin + other.getXsize() * 0.5D, margin + other.getYsize() * 0.5D, margin + other.getZsize() * 0.5D);
            if(distance >= margin && corridor.clip(other.getCenter(), other.getCenter().add(relative.scale(horizon))).isEmpty()) continue;
            double score = distance / required;
            if(score < risk){ risk = score; res = new ReactiveCollisionAvoidance.ProximityHazard(direction, distance, required); }
        }
        return res;
    }

    private static double gap(double min, double max, double otherMin, double otherMax){
        return otherMin > max ? otherMin - max : otherMax < min ? otherMax - min : 0;
    }

    private static Vec3 vector(Random random, double span){
        return new Vec3((random.nextDouble() - 0.5D) * span, (random.nextDouble() - 0.5D) * span, (random.nextDouble() - 0.5D) * span);
    }
}
