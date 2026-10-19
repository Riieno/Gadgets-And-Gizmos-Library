package com.rieno.gadgetsandgizmos.lib.navigation;

import com.rieno.gadgetsandgizmos.lib.physics.SubLevelParticleOcclusion;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmBuiltinControlModes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Check stopped craft, side traffic and safe recovery from a forced physical overlap
class ProximityCollisionAvoidanceTest{
    private static final AABB HULL = new AABB(-1, -1, -1, 1, 1, 1);

    @Test void approachingSideTrafficWakesAStoppedCraft(){
        var hazard = hazard(new AABB(3, -1, -1, 5, 1, 1), new Vec3(-3, 0, 0));
        assertTrue(hazard.available());
        var res = ReactiveCollisionAvoidance.resolveProximity(hazard, List.of(
                new ReactiveCollisionAvoidance.EscapeCandidate(new Vec3(-1, 0, 0), 8),
                new ReactiveCollisionAvoidance.EscapeCandidate(new Vec3(0, 1, 0), 8),
                new ReactiveCollisionAvoidance.EscapeCandidate(new Vec3(1, 0, 0), 2)));
        assertTrue(res.escapeAvailable());
        assertEquals(new Vec3(-1, 0, 0), res.escapeDirection());
        Vec3 direction = res.escapeDirection();
        var input = new ScmControlMode.ControlInput(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(1, 0, 0), direction.scale(0.5D),
                direction, Vec3.ZERO, 1, 0.0625D, 0.6D, false, 8, 8, 1, -1,
                true, false, false, 0, 0, null);
        var output = ScmControlModeRegistry.resolve(ScmBuiltinControlModes.AIRSHIP_ID.toString()).navigate(input);
        assertTrue(output.force().dot(direction) > 0.0D, "Proximity escape lost translation control");
    }

    @Test void stationaryNeighboursRemainHazardousInsideTheClearanceBand(){
        assertTrue(hazard(new AABB(1.5D, -1, -1, 3.5D, 1, 1), Vec3.ZERO).available());
        assertFalse(hazard(new AABB(3, -1, -1, 5, 1, 1), new Vec3(3, 0, 0)).available());
        assertFalse(hazard(new AABB(3, -1, -1, 5, 1, 1), Vec3.ZERO).available());
    }

    @Test void overheadAndUnderneathTrafficCanSelectVerticalSeparation(){
        for(int sign : new int[]{-1, 1}){
            AABB other = HULL.move(0, sign * 2.5D, 0);
            var hazard = hazard(other, Vec3.ZERO);
            Vec3 depart = new Vec3(0, -sign, 0);
            var res = ReactiveCollisionAvoidance.resolveProximity(hazard,
                    List.of(new ReactiveCollisionAvoidance.EscapeCandidate(depart, 4)));
            assertEquals(depart, res.escapeDirection());
        }
    }

    @Test void trafficThatPassesBesideTheHullDoesNotTriggerAnEscape(){
        assertFalse(hazard(new AABB(3, -1, 6, 5, 1, 8), new Vec3(-8, 0, 0)).available());
    }

    @Test void physicalOverlapCanBeLeftButCannotBeDeepened(){
        AABB obstacle = new AABB(0.75D, -1, -1, 2.75D, 1, 1);
        assertTrue(SubLevelParticleOcclusion.allowsOverlapDeparture(List.of(HULL), obstacle, new Vec3(-1, 0, 0), 4));
        assertFalse(SubLevelParticleOcclusion.allowsOverlapDeparture(List.of(HULL), obstacle, new Vec3(1, 0, 0), 4));
        assertFalse(SubLevelParticleOcclusion.allowsOverlapDeparture(List.of(HULL, HULL.move(4, 0, 0)),
                obstacle, new Vec3(-1, 0, 0), 4));
    }

    private static ReactiveCollisionAvoidance.ProximityHazard hazard(AABB obstacle, Vec3 velocity){
        return ReactiveCollisionAvoidance.proximityHazard(List.of(HULL), Vec3.ZERO,
                List.of(new ReactiveCollisionAvoidance.MovingObstacle(obstacle, velocity)), 1, 1);
    }
}
