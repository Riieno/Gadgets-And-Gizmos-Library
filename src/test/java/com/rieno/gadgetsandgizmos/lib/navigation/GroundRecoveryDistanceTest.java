package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroundRecoveryDistanceTest{
    @Test
    void brakingUsesTheCommittedPathEndInsteadOfTheNearbyLookahead(){
        var curves = List.of(GroundPathPlanner.Curve.straight(Vec3.ZERO, new Vec3(10.0D, 0.0D, 0.0D), false),
                GroundPathPlanner.Curve.straight(new Vec3(10.0D, 0.0D, 0.0D), new Vec3(30.0D, 0.0D, 0.0D), false));
        assertEquals(29.0D, GroundPathPlanner.remainingManeuverDistance(new Vec3(1.0D, 0.0D, 0.0D), curves, 0));
        assertEquals(20.75D, GroundPathPlanner.remainingManeuverDistance(new Vec3(9.25D, 0.0D, 0.0D), curves, 0));
    }

    @Test
    void recoveryStillStopsForAnActualGearChange(){
        var curves = List.of(GroundPathPlanner.Curve.straight(Vec3.ZERO, new Vec3(10.0D, 0.0D, 0.0D), false),
                GroundPathPlanner.Curve.straight(new Vec3(10.0D, 0.0D, 0.0D), Vec3.ZERO, true));
        assertEquals(0.75D, GroundPathPlanner.remainingManeuverDistance(new Vec3(9.25D, 0.0D, 0.0D), curves, 0), 1.0E-9D);
        assertEquals(0.0D, GroundPathPlanner.remainingManeuverDistance(Vec3.ZERO, curves, -1));
    }
}
