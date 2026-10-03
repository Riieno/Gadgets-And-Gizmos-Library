package com.rieno.gadgetsandgizmos.lib.worker;

import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkerPathingRecoveryTest{
    // Keep searching around an obstacle instead of repeatedly accepting the same nearest prefix
    @Test void waitsForTheDetourSearchToFinish(){
        Level level = mock(Level.class);
        var planner = mock(SablePathfinder.QueuedPlan.class);
        Vec3 target = new Vec3(8, 64, 0);
        Vec3 start = new Vec3(0, 64, 0);
        when(planner.result()).thenReturn(new SablePathfinder.Result(SablePathfinder.Outcome.LIMIT_REACHED,
                List.of(new SablePathfinder.Waypoint(new Vec3(1, 64, 0), SablePathfinder.RouteMode.FLIGHT)), 96));
        when(planner.finished()).thenReturn(false);
        try(var pathing = mockStatic(WorkerPathing.class, CALLS_REAL_METHODS)){
            pathing.when(() -> WorkerPathing.queue(level, start, target, 512, true)).thenReturn(planner);
            var navigator = WorkerPathing.liveNavigator(512, true);
            assertEquals(start, navigator.advance(level, start, target, 96, 0.18).position());
            assertEquals(start, navigator.advance(level, start, target, 96, 0.18).position());
            verify(planner, times(2)).advance(96);
            when(planner.finished()).thenReturn(true);
            navigator.advance(level, start, target, 96, 0.18);
            assertNotEquals(start, navigator.advance(level, start, target, 96, 0.18).position());
        }
    }

    // Retry an unavailable load-time route using a fresh planner
    @Test void retriesAfterUnavailableChunksBecomeReady(){
        Level level = mock(Level.class);
        var unavailable = mock(SablePathfinder.QueuedPlan.class);
        var ready = mock(SablePathfinder.QueuedPlan.class);
        Vec3 start = new Vec3(0, 64, 0);
        Vec3 target = new Vec3(8, 64, 0);
        when(unavailable.finished()).thenReturn(true);
        when(unavailable.result()).thenReturn(new SablePathfinder.Result(SablePathfinder.Outcome.UNAVAILABLE, List.of(), 0));
        when(ready.finished()).thenReturn(true);
        when(ready.result()).thenReturn(new SablePathfinder.Result(SablePathfinder.Outcome.COMPLETE,
                List.of(new SablePathfinder.Waypoint(target, SablePathfinder.RouteMode.FLIGHT)), 1));
        try(var pathing = mockStatic(WorkerPathing.class, CALLS_REAL_METHODS)){
            pathing.when(() -> WorkerPathing.queue(level, start, target, 512, true)).thenReturn(unavailable, ready);
            var navigator = WorkerPathing.liveNavigator(512, true);
            assertTrue(navigator.advance(level, start, target, 96, 0.18).unavailable());
            assertFalse(navigator.advance(level, start, target, 96, 0.18).unavailable());
            assertNotEquals(start, navigator.advance(level, start, target, 96, 0.18).position());
        }
    }
}
