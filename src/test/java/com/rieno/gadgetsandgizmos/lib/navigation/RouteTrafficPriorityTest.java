package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// A lane follower yields to its leader independently of small centreline corrections
class RouteTrafficPriorityTest{
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
