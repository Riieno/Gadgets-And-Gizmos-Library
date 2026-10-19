package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ViewTargetLockTest{
    private static ViewTargetLock.Target<String> target(String key, double distance){
        return new ViewTargetLock.Target<>(key, new AABB(-1, -1, distance, 1, 1, distance + 2));
    }

    @Test void acquiresTheNearestHitInsideTheRange(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var near = target("near", 8);
        var far = target("far", 40);
        assertEquals(near, lock.update(Vec3.ZERO, 16, List.of(far, near), key -> null));
        assertEquals("near", lock.key());
    }

    @Test void retainsTheSameMovingTargetWhenAnotherHitIsCloser(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var first = target("first", 10);
        lock.update(Vec3.ZERO, 32, List.of(first), key -> null);
        var moved = target("first", 20);
        assertEquals(moved, lock.update(Vec3.ZERO, 32, List.of(target("new", 1)), key -> moved));
    }

    @Test void avoidsResolvingCandidateGeometryWhileItsLockIsValid(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var hit = target("hit", 10);
        lock.update(Vec3.ZERO, 32, List.of(hit), key -> null);
        assertEquals(hit, lock.update(Vec3.ZERO, 32, () -> {
            fail("A retained target should not resolve candidates");
            return List.of();
        }, key -> hit));
    }

    @Test void releasesUnavailableAndOutOfRangeTargets(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var first = target("first", 10);
        var next = target("next", 5);
        lock.update(Vec3.ZERO, 16, List.of(first), key -> null);
        assertEquals(next, lock.update(Vec3.ZERO, 16, List.of(next), key -> target(key, 17)));
        assertNull(lock.update(Vec3.ZERO, 16, List.of(), key -> null));
        assertNull(lock.key());
    }

    @Test void usesTheNearestSurfaceOfLargeTargets(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var large = new ViewTargetLock.Target<>("large", new AABB(-10, -10, 16, 10, 10, 100));
        assertEquals(large, lock.update(Vec3.ZERO, 16, List.of(large), key -> null));
        assertEquals(256, ViewTargetLock.distanceSquared(Vec3.ZERO, large.bounds()), 1.0E-9);
    }

    @Test void disablingAndInvalidRangesCannotRetainALock(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var hit = target("hit", 1);
        Map<String, ViewTargetLock.Target<String>> live = Map.of("hit", hit);
        for(double range : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}){
            lock.update(Vec3.ZERO, 16, List.of(hit), live::get);
            assertNull(lock.update(Vec3.ZERO, range, List.of(hit), live::get));
        }
        lock.update(Vec3.ZERO, 16, List.of(hit), live::get);
        lock.clear();
        assertNull(lock.key());
    }

    @Test void dynamicAcquisitionSwitchesToCloserFreshHits(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var far = target("far", 20);
        var near = target("near", 5);
        var nearer = target("nearer", 2);
        lock.update(Vec3.ZERO, 32, List.of(far), key -> null, false);
        assertEquals(near, lock.update(Vec3.ZERO, 32, List.of(far, near), key -> far, false));
        assertEquals("near", lock.key());
        assertEquals(nearer, lock.update(Vec3.ZERO, 32, List.of(nearer, near), key -> near, false));
    }

    @Test void lockingKeepsTheCurrentTargetAndUnlockingResumesAcquisition(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var far = target("far", 20);
        var near = target("near", 5);
        lock.update(Vec3.ZERO, 32, List.of(far), key -> null, false);
        assertEquals(far, lock.update(Vec3.ZERO, 32, () -> {
            fail("Locked targets must not run acquisition");
            return List.of();
        }, key -> far, true));
        assertEquals(near, lock.update(Vec3.ZERO, 32, List.of(far, near), key -> far, false));
    }

    @Test void aLostLockedTargetReacquiresAndDynamicAcquisitionDropsUnseenTargets(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var far = target("far", 20);
        var near = target("near", 5);
        lock.update(Vec3.ZERO, 32, List.of(far), key -> null, true);
        assertEquals(near, lock.update(Vec3.ZERO, 32, List.of(near), key -> null, true));
        assertNull(lock.update(Vec3.ZERO, 32, List.of(), key -> near, false));
        assertNull(lock.key());
    }

    @Test void equalDistanceHitsKeepTheAlreadySelectedIdentity(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var first = target("first", 5);
        var equal = target("equal", 5);
        lock.update(Vec3.ZERO, 32, List.of(first), key -> null, false);
        assertEquals(first, lock.update(Vec3.ZERO, 32, List.of(equal, first), key -> first, false));
    }

    @Test void dynamicSelectionIgnoresOutOfRangeHitsAndFollowsChangedDistances(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var first = target("first", 5);
        lock.update(Vec3.ZERO, 32, List.of(first), key -> null, false);
        var moved = target("first", 25);
        var other = target("other", 15);
        assertEquals(other, lock.update(Vec3.ZERO, 32, List.of(target("outside", 33), moved, other), key -> moved, false));
    }

    @Test void aRetainedUpdateBetweenSamplesDoesNotInventFreshDetections(){
        ViewTargetLock<String> lock = new ViewTargetLock<>();
        var first = target("first", 10);
        lock.update(Vec3.ZERO, 32, List.of(first), key -> null, false);
        assertEquals(first, lock.update(Vec3.ZERO, 32, List.of(), key -> first, true));
        assertNull(lock.update(Vec3.ZERO, 32, List.of(), key -> null, true));
    }
}
