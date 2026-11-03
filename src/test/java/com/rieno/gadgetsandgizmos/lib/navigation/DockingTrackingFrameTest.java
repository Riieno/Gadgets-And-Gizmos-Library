package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedDockingApi;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Keep base-centred route geometry separate from extended docking contact points
class DockingTrackingFrameTest{
    // Preserve the connector's base through every mounting direction and a rotated body frame
    @Test
    void baseAndTipRemainDistinctInRotatedFrames(){
        Vec3 base = new Vec3(14, 32, -7);
        var rotation = new Quaterniond().rotateXYZ(0.3D, -0.7D, 0.2D);
        for(Direction dir : Direction.values()){
            var normal = dir.getNormal();
            var rotated = rotation.transform(new Vector3d(normal.getX(), normal.getY(), normal.getZ()));
            Vec3 facing = new Vec3(rotated.x, rotated.y, rotated.z);
            Vec3 tip = SimulatedDockingApi.tipPosition(base, facing);
            assertEquals(1.5D, base.distanceTo(tip), 1.0E-9D);
            assertEquals(0, base.distanceTo(SimulatedDockingApi.basePosition(tip, facing)), 1.0E-9D);
        }
    }

    // The final base-centred hull stops above the station contact instead of sweeping below its berth
    @Test
    void approachAssessmentUsesTheTrackedDockingPose(){
        Vec3 tip = Vec3.ZERO, facing = new Vec3(0, 1, 0), up = new Vec3(0, 0, 1);
        var path = path(tip, facing, up);
        var hull = List.of(new AABB(-0.1D, -0.1D, -0.1D, 0.1D, 0.1D, 0.1D));
        var obstacles = List.of(new DockTrafficGeometry.Obstacle(UUID.randomUUID(),
                List.of(new AABB(-1, -0.5D, -1, 1, 0.5D, 1))));
        Vec3 base = SimulatedDockingApi.basePosition(tip, facing.scale(-1));
        assertFalse(DockTrafficGeometry.assess(path, tip, facing, up, new Vec3(20, 10, 0), hull, obstacles).reachable());
        assertTrue(DockTrafficGeometry.assess(path, tip, facing, up, new Vec3(20, 10, 0), hull, obstacles,
                null, false, DockTrafficGeometry.Traffic.EMPTY, base).reachable());
    }

    // Keep the network fixed while reserving departure from the vessel's actual base-centred pose
    @Test
    void departureUsesTheTrackedOriginWithoutShiftingTheLane(){
        Vec3 tip = Vec3.ZERO, facing = new Vec3(0, 1, 0), up = new Vec3(0, 0, 1);
        var path = path(tip, facing, up);
        var hull = List.of(new AABB(-0.1D, -0.1D, -0.1D, 0.1D, 0.1D, 0.1D));
        var obstacles = List.of(new DockTrafficGeometry.Obstacle(UUID.randomUUID(),
                List.of(new AABB(-1, -0.5D, -1, 1, 0.5D, 1))));
        Vec3 arrival = new Vec3(0, 10, 0), base = SimulatedDockingApi.basePosition(tip, facing.scale(-1));
        assertTrue(DockingDeparturePlan.select(path, tip, facing, up, arrival, hull, obstacles, DockTrafficGeometry.Traffic.EMPTY).isEmpty());
        var plan = DockingDeparturePlan.select(path, tip, facing, up, arrival, base, hull, obstacles,
                DockTrafficGeometry.Traffic.EMPTY).orElseThrow();
        assertEquals(base, plan.tip());
        assertEquals(0, plan.clearancePoint().distanceTo(arrival), 0.01D);
        assertEquals(plan.clearancePoint(), plan.exitControls().getFirst());
        Vec3 contact = SimulatedDockingApi.tipPosition(plan.clearancePoint(), facing.scale(-1));
        assertEquals(0, SimulatedDockingApi.basePosition(contact, facing.scale(-1)).distanceTo(arrival), 0.01D);
    }

    // Supply one lane crossing the station's vertical connector axis
    private static DockingApproachPath path(Vec3 tip, Vec3 facing, Vec3 up){
        return DockingApproachPath.capture(tip, facing, up, new Vec3(0, 10, 0),
                List.of(new Vec3(20, 10, 0), new Vec3(-20, 10, 0)));
    }
}
