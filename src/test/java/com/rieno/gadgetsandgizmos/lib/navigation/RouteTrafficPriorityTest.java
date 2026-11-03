package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

// A lane follower yields to its leader independently of small centreline corrections
class RouteTrafficPriorityTest{
    // A shared future region cannot turn clear separated lanes into an immediate stop
    @Test
    void recordedSeparatedLanesKeepTravelRoom() throws Exception{
        try(var input = getClass().getResourceAsStream("/navigation/separated-traffic.json")){
            assertNotNull(input);
            var traffic = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), RouteTrafficPriority.Participant[].class);
            for(int idx = 0; idx < traffic.length; idx++){
                var res = RouteTrafficPriority.resolveCoordinated(traffic[idx], List.of(traffic[1 - idx]),
                        new RouteTrafficPriority.Settings(32, 32, 2, 0, 0.35D, 0.5D));
                assertTrue(!res.yield() || res.yieldDistance() > 2,
                        "Separated ships were stopped before their hulls approached: " + res);
            }
        }
    }

    // Unrelated routes at different heights must not create fleet-wide yield dependencies
    @Test
    void distinctDocksAndSchedulesDoNotShareTrafficOwnership(){
        var hull = List.of(new AABB(-12, -2, -2, 12, 3, 2));
        var first = ship(1, new Vec3(0, 0, 0), new Vec3(24, 0, 0), hull);
        var second = ship(2, new Vec3(0, 12, 0), new Vec3(24, 12, 0), hull);
        var third = ship(3, new Vec3(0, 24, 0), new Vec3(24, 24, 0), hull);
        for(var subject : List.of(first, second, third)){
            assertFalse(RouteTrafficPriority.resolveCoordinated(subject, List.of(first, second, third), null).conflict());
        }
    }

    // A stopped follower must not reserve a clear leader's escape from the queue
    @Test
    void physicalHullQueueKeepsTheLeaderMoving(){
        var hull = List.of(new AABB(-10, -2, -2, 10, 3, 2));
        for(double correction : new double[]{-0.5D, 0, 0.5D}){
            var leader = new RouteTrafficPriority.Participant(new UUID(0, 2), new Vec3(16, 0, correction), new Vec3(4, 0, 0),
                    List.of(new Vec3(16, 0, correction), new Vec3(16, 0, 0), new Vec3(40, 0, 0)),
                    SablePathfinder.RouteMode.FLIGHT, 12, 3, 4, hull);
            var follower = ship(1, Vec3.ZERO, new Vec3(32, 0, 0), hull);
            assertFalse(RouteTrafficPriority.resolveCoordinated(leader, List.of(follower), null).yield());
            assertTrue(RouteTrafficPriority.resolveCoordinated(follower, List.of(leader), null).yield());
        }
    }

    // True crossings retain deterministic priority after the narrow hull sweep
    @Test
    void physicalCrossingsStillYield(){
        var hull = List.of(new AABB(-2, -2, -2, 2, 2, 2));
        var first = ship(1, new Vec3(-12, 0, 0), new Vec3(12, 0, 0), hull);
        var second = ship(2, new Vec3(0, 0, -12), new Vec3(0, 0, 12), hull);
        assertFalse(RouteTrafficPriority.resolveCoordinated(first, List.of(second), null).yield());
        assertTrue(RouteTrafficPriority.resolveCoordinated(second, List.of(first), null).yield());
    }

    // Intersecting future paths must not charge the ship's full radius a second time
    @Test
    void approachingAMergeKeepsTheClearLanePrefix(){
        var hull = List.of(new AABB(-2, -2, -2, 2, 2, 2));
        var first = ship(1, Vec3.ZERO, new Vec3(30, 0, 0), hull);
        var second = ship(2, new Vec3(0, 12, 0), new Vec3(30, 0, 0), hull);
        var res = RouteTrafficPriority.resolveCoordinated(second, List.of(first), null);
        assertTrue(res.conflict());
        assertTrue(res.yieldDistance() > 12, "A future merge consumed travel on the separated lane: " + res);
    }

    private static RouteTrafficPriority.Participant ship(int id, Vec3 start, Vec3 end, List<AABB> hull){
        return new RouteTrafficPriority.Participant(new UUID(0, id), start, end.subtract(start).normalize().scale(4),
                List.of(start, end), SablePathfinder.RouteMode.FLIGHT, 16, 3, 4, hull);
    }

    // A short correction toward the lane must not become a head-on conflict with a follower
    @Test
    void leaderDoesNotYieldToFollowingTraffic(){
        for(var mode : List.of(SablePathfinder.RouteMode.FLIGHT, SablePathfinder.RouteMode.GROUND)){
            for(double correction : new double[]{-2, -1.25D, -0.75D, -0.4D, -0.025D, 0, 0.025D, 0.4D, 0.75D, 1.25D, 2}){
                var leader = participant(2, 16, correction, 4, mode);
                var follower = participant(1, 0, -correction, 4, mode);
                var res = RouteTrafficPriority.resolveCoordinated(leader, List.of(follower), null);
                assertFalse(res.yield(), "A leader yielded to its follower during a " + correction + " block correction: " + res);
                assertTrue(RouteTrafficPriority.resolveCoordinated(follower, List.of(leader), null).yield(),
                        "A close follower did not yield to its leader");
            }
        }
    }

    // Reversing UUID priority cannot make the lead vessel wait for the rest of its queue
    @Test
    void stoppedFollowersDoNotHoldMovingLeaders(){
        for(int idx = 0; idx < 16; idx++){
            var leader = participant(idx + 1, 16, .1D, 4, SablePathfinder.RouteMode.FLIGHT);
            var follower = participant(16 - idx, 0, -.1D, 0, SablePathfinder.RouteMode.FLIGHT);
            if(leader.vehicleId().equals(follower.vehicleId())) continue;
            assertFalse(RouteTrafficPriority.resolve(leader, List.of(follower), null).yield());
        }
    }

    // Opposing travel retains its existing deterministic right-of-way
    @Test
    void opposingTrafficStillYields(){
        var first = new RouteTrafficPriority.Participant(new UUID(0, 1), Vec3.ZERO, new Vec3(4, 0, 0),
                List.of(Vec3.ZERO, new Vec3(32, 0, 0)), SablePathfinder.RouteMode.FLIGHT, 2, 2, 4);
        var second = new RouteTrafficPriority.Participant(new UUID(0, 2), new Vec3(16, 0, 0), new Vec3(-4, 0, 0),
                List.of(new Vec3(16, 0, 0), new Vec3(-16, 0, 0)), SablePathfinder.RouteMode.FLIGHT, 2, 2, 4);
        assertFalse(RouteTrafficPriority.resolve(first, List.of(second), null).yield());
        assertTrue(RouteTrafficPriority.resolve(second, List.of(first), null).yield());
    }

    // Publish the actual position, centreline projection and remaining directed lane
    private static RouteTrafficPriority.Participant participant(int id, double x, double z, double speed,
                                                                SablePathfinder.RouteMode mode){
        Vec3 pos = new Vec3(x, 0, z);
        return new RouteTrafficPriority.Participant(new UUID(0, id), pos, new Vec3(speed, 0, 0),
                List.of(pos, new Vec3(x, 0, 0), new Vec3(40, 0, 0)), mode, 10, 3, 4);
    }
}
