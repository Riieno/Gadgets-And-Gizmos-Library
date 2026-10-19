package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.ObstacleDetourPlanner;
import com.rieno.gadgetsandgizmos.lib.navigation.ReactiveCollisionAvoidance;
import com.rieno.gadgetsandgizmos.lib.scm.ScmBuiltinControlModes;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSpeedControl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Check close-hull escapes through shared clearance, bypass planning and propulsion control
class CollisionContactEscapeTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double PADDING = 0.125D;
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);
    private static final Vec3 RIGHT = new Vec3(1, 0, 0);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep inward motion blocked while allowing short departures and parallel motion
    @Test void overlappingPaddingDoesNotBlockSafeDirections(){
        AABB moving = body(Vec3.ZERO).inflate(PADDING);
        List<AABB> obstacles = List.of(body(new Vec3(2.05D, 0, 0)));
        assertEquals(0.0D, SubLevelParticleOcclusion.findEnvelopeBlockingDistance(
                RIGHT.scale(-1), 8, List.of(moving), obstacles));
        assertEquals(8.0D, clearance(moving, obstacles, RIGHT.scale(-1), 8));
        assertEquals(8.0D, clearance(moving, obstacles, FORWARD, 8));
        assertEquals(0.0D, clearance(moving, obstacles, RIGHT, 8));
        assertEquals(0.01D, clearance(moving, obstacles, RIGHT.scale(-1), 0.01D));
    }

    // Refuse to infer an escape through actual physical penetration
    @Test void physicalOverlapRemainsBlocked(){
        AABB moving = body(Vec3.ZERO).inflate(PADDING);
        List<AABB> obstacles = List.of(body(new Vec3(1.9D, 0, 0)));
        for(Vec3 dir : List.of(RIGHT, RIGHT.scale(-1), FORWARD, FORWARD.scale(-1))){
            assertEquals(0.0D, clearance(moving, obstacles, dir, 8));
        }
    }

    // Check every obstacle and compound-hull part after leaving initial padding contact
    @Test void escapingOneShipStillStopsBeforeAnother(){
        AABB moving = body(Vec3.ZERO).inflate(PADDING);
        List<AABB> obstacles = List.of(body(new Vec3(2.05D, 0, 0)), body(new Vec3(-5, 0, 0)));
        assertEquals(2.875D, clearance(moving, obstacles, RIGHT.scale(-1), 8), 1.0E-8D);
        assertEquals(0.0D, SubLevelParticleOcclusion.findEnvelopeBlockingDistance(
                RIGHT.scale(-1), 8, List.of(moving, body(new Vec3(-3, 0, 0)).inflate(PADDING)),
                obstacles, PADDING));
    }

    // Apply the contact policy to exact sweeps without reducing future clearance padding
    @Test void exactSweepsAllowDepartureAndRejectNewContact(){
        AABB physical = body(Vec3.ZERO);
        AABB moving = physical.inflate(PADDING);
        AABB obstacle = body(new Vec3(2.05D, 0, 0));
        assertNull(sweep(moving, physical, obstacle, RIGHT.scale(-0.01D)));
        assertNull(sweep(moving, physical, obstacle, FORWARD.scale(3)));
        assertEquals(0.0D, sweep(moving, physical, obstacle, RIGHT));
        assertEquals(0.0D, sweep(moving, physical, body(new Vec3(1.9D, 0, 0)), RIGHT.scale(-3)));
        Double hit = sweep(moving, physical, body(new Vec3(-5, 0, 0)), RIGHT.scale(-8));
        assertNotNull(hit);
        assertTrue(hit > 0.0D && hit < 1.0D);
    }

    // Retain the old strict policy when callers do not supply their physical padding
    @Test void strictSweepAndInvalidAllowancesStayConservative(){
        AABB moving = body(Vec3.ZERO).inflate(PADDING);
        AABB obstacle = body(new Vec3(2.05D, 0, 0));
        assertEquals(0.0D, SubLevelParticleOcclusion.sweptBoundsIntersectionFraction(
                Vec3.ZERO, RIGHT.scale(-3), moving, obstacle, 0.015625D));
        for(double allowance : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 4}){
            assertEquals(0.0D, SubLevelParticleOcclusion.findEnvelopeBlockingDistance(
                    RIGHT.scale(-1), 8, List.of(moving), List.of(obstacle), allowance));
        }
    }

    // Start a live reaction when the old envelope policy would reject every probe
    @Test void reactiveEscapeHasPositivePropulsionClearance(){
        AABB moving = body(Vec3.ZERO).inflate(PADDING);
        List<AABB> obstacles = List.of(body(new Vec3(2.05D, 0, 0)));
        double direct = clearance(moving, obstacles, RIGHT, 8);
        var response = ReactiveCollisionAvoidance.resolve(new ReactiveCollisionAvoidance.Request(
                RIGHT, direct, 2, List.of(new ReactiveCollisionAvoidance.EscapeCandidate(
                FORWARD, clearance(moving, obstacles, FORWARD, 8)))));
        assertTrue(response.escapeAvailable());
        assertTrue(ScmSpeedControl.scannedSafeSpeed(clearance(moving, obstacles,
                response.escapeDirection(), 8), 8, 2, 0.25D, 0.35D, 4) > 0.0D);
    }

    // Preserve sideways thrust until avoidance has completed and heading preference can resume
    @Test void airshipEscapeKeepsLateralPropulsion(){
        boolean prefer = ReactiveCollisionAvoidance.retainForwardPreference(true, true);
        var input = new ScmControlMode.ControlInput(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                FORWARD, new Vec3(0, 1, 0), RIGHT, RIGHT.scale(4), RIGHT, Vec3.ZERO,
                2, 0.25D, 0.6D, true, 0, 8, 2, -1,
                true, prefer, false, 0, 0, null);
        var output = ScmControlModeRegistry.resolve(ScmBuiltinControlModes.AIRSHIP_ID.toString()).navigate(input);
        assertTrue(output.force().dot(RIGHT) > 0.0D);
        assertTrue(ReactiveCollisionAvoidance.retainForwardPreference(true, false));
        assertFalse(ReactiveCollisionAvoidance.retainForwardPreference(false, false));
    }

    // Execute both bypasses from a physical gap smaller than the safety margin
    @Test void twoCloseStoppedShipsCompleteOpposingBypasses(){
        Vec3 first = new Vec3(0, 0, -1.025D);
        Vec3 second = new Vec3(0, 0, 1.025D);
        Vec3 firstGoal = new Vec3(0, 0, 10);
        Vec3 secondGoal = new Vec3(0, 0, -10);
        List<Vec3> firstPlan = plan(first, firstGoal, second);
        List<Vec3> secondPlan = plan(second, secondGoal, first);
        int firstIdx = 0;
        int secondIdx = 0;
        for(int tick = 0; tick < 600 && (firstIdx < firstPlan.size() || secondIdx < secondPlan.size()); tick++){
            Vec3 nextFirst = firstIdx < firstPlan.size() ? move(first, firstPlan.get(firstIdx), second) : first;
            Vec3 nextSecond = secondIdx < secondPlan.size() ? move(second, secondPlan.get(secondIdx), nextFirst) : second;
            assertFalse(body(nextFirst).intersects(body(nextSecond)));
            first = nextFirst;
            second = nextSecond;
            if(firstIdx < firstPlan.size() && first.distanceToSqr(firstPlan.get(firstIdx)) < 1.0E-8D) firstIdx++;
            if(secondIdx < secondPlan.size() && second.distanceToSqr(secondPlan.get(secondIdx)) < 1.0E-8D) secondIdx++;
        }
        assertEquals(firstGoal, first);
        assertEquals(secondGoal, second);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Measure a translated padded hull against all supplied obstacles
    private static double clearance(AABB moving, List<AABB> obstacles, Vec3 dir, double range){
        return SubLevelParticleOcclusion.findEnvelopeBlockingDistance(dir, range,
                List.of(moving), obstacles, PADDING);
    }

    // Check the exact shape sweep with the original physical hull
    private static Double sweep(AABB moving, AABB physical, AABB obstacle, Vec3 delta){
        return SubLevelParticleOcclusion.sweptBoundsIntersectionFraction(
                Vec3.ZERO, delta, moving, obstacle, 0.015625D, physical);
    }

    // Prepare a complete three-leg bypass using the same contact-aware clearance as live control
    private static List<Vec3> plan(Vec3 start, Vec3 target, Vec3 other){
        var plan = ObstacleDetourPlanner.queue(new ObstacleDetourPlanner.Request(
                start, target, target.subtract(start), 2.5D, 2.5D, 32, true, true));
        plan.advance((from, to) -> clearance(body(from).inflate(PADDING), List.of(body(other)),
                to.subtract(from), from.distanceTo(to)) >= from.distanceTo(to) - 1.0E-6D, 1000, () -> true);
        assertTrue(plan.finished());
        assertFalse(plan.waypoints().isEmpty());
        return plan.waypoints();
    }

    // Advance only when the selected escape retains a positive collision speed limit
    private static Vec3 move(Vec3 start, Vec3 target, Vec3 other){
        Vec3 offset = target.subtract(start);
        double free = clearance(body(start).inflate(PADDING), List.of(body(other)), offset, 16);
        double speed = ScmSpeedControl.scannedSafeSpeed(free, 16, 2, 0.25D, 0.35D, 4);
        assertTrue(speed > 0.0D, "Escape lost propulsion at " + start);
        return offset.length() <= 0.1D ? target : start.add(offset.normalize().scale(0.1D));
    }

    // Represent one physical ship without navigation padding
    private static AABB body(Vec3 pos){
        return new AABB(pos.x - 1, pos.y - 1, pos.z - 1, pos.x + 1, pos.y + 1, pos.z + 1);
    }
}
