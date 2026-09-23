package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmCarReverseSteeringTest {
    @Test
    void routeYawUsesStableWheelHandednessAndReverseInvertsItOnce() {
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 desiredVehicleFacing = new Vec3(0.0D, 0.0D, 1.0D);
        ScmControlMode car = ScmControlModeRegistry.resolve("car");

        ScmControlMode.ControlOutput forwardTurn = car.navigate(input(
                forward, up, desiredVehicleFacing, false));
        ScmControlMode.ControlOutput reverseTurn = car.navigate(input(
                forward, up, desiredVehicleFacing.scale(-1.0D), true));

        assertEquals(1.0D, forwardTurn.driveDirection(), 1.0E-8D);
        assertEquals(-1.0D, reverseTurn.driveDirection(), 1.0E-8D);
        assertTrue(forwardTurn.force().dot(forward) > 0.0D);
        assertTrue(reverseTurn.force().dot(forward) < 0.0D);

        // Both manoeuvres turn the vehicle body towards +Z. Reverse changes
        // the travel tangent, not the physical body-yaw target.
        assertTrue(forwardTurn.torque().dot(up) < 0.0D);
        assertEquals(forwardTurn.torque().dot(up),
                reverseTurn.torque().dot(up), 1.0E-8D);

        // Wheel steering reverses at the actuator boundary exactly once.
        double forwardYaw = ScmControlAxes.yawRightDemand(
                forwardTurn.torque(), up, false);
        double reverseYaw = ScmControlAxes.yawRightDemand(
                reverseTurn.torque(), up, true);
        double forwardWheels = ScmControlAxes.wheelSteeringDemand(
                forwardTurn.torque(), up, false);
        double reverseWheels = ScmControlAxes.wheelSteeringDemand(
                reverseTurn.torque(), up, true);
        assertTrue(forwardYaw > 0.0D);
        assertTrue(reverseYaw < 0.0D);
        assertTrue(forwardWheels < 0.0D);
        assertTrue(reverseWheels > 0.0D);
        assertEquals(forwardTurn.torque().dot(up), forwardWheels, 1.0E-8D);
        assertEquals(forwardYaw, -reverseYaw, 1.0E-8D);
        assertEquals(forwardWheels, -reverseWheels, 1.0E-8D);
    }

    @Test
    void smallHeadingErrorRetainsItsWheelAngleWhileMoving(){
        double error = Math.toRadians(3.0D);
        double steering = ScmControlAxes.groundSteeringDemand(error, 0.0D);

        assertEquals(0.1D, steering, 1.0E-8D);
        assertTrue(steering > 0.0D);
    }

    @Test
    void automaticStraightRouteDrivesFromRestAndCurveFeedForwardKeepsYawActive(){
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        var input = new ScmControlMode.ControlInput(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                forward, up, forward.cross(up), forward.scale(8.0D), forward, Vec3.ZERO,
                8.0D, 0.75D, 0.6D, true, 40.0D, 40.0D, 8.0D,
                ScmSpeedControl.propulsionRequest(-1.0D), true, true, false, 0.2D, -0.8D);
        var output = ScmControlModeRegistry.resolve("car").navigate(input);
        assertTrue(output.force().dot(forward) > 0.9D);
        assertTrue(output.driveStrength() > 0.9D);
        assertEquals(-0.8D, output.torque().dot(up), 1.0E-8D);
        assertEquals(1.0D, output.driveDirection(), 1.0E-8D);
    }

    @Test
    void cruiseKeepsTheSpeedChainAndSteeringActiveTogether(){
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        var input = new ScmControlMode.ControlInput(Vec3.ZERO, forward.scale(8.0D), Vec3.ZERO,
                forward, up, forward.cross(up), forward.scale(8.0D), forward, Vec3.ZERO,
                8.0D, 0.75D, 0.6D, true, 40.0D, 40.0D, 8.0D,
                1.0D, true, true, false, 0.2D, -0.8D);
        var output = ScmControlModeRegistry.resolve("car").navigate(input);
        assertEquals(1.0D, output.driveStrength(), 1.0E-8D);
        assertEquals(-0.8D, output.torque().dot(up), 1.0E-8D);
    }

    @Test
    void turningCannotOverrideAnExplicitStopOrZeroCollisionSpeedCap(){
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 path = new Vec3(0.0D, 0.0D, 1.0D);
        for(double[] request : new double[][]{{0.0D, 8.0D}, {-1.0D, 0.0D}}){
            var input = new ScmControlMode.ControlInput(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                    forward, up, forward.cross(up), path.scale(8.0D), path, Vec3.ZERO,
                    8.0D, 0.75D, 0.6D, true, 40.0D, 40.0D, request[1],
                    request[0], true, true, false);
            var output = ScmControlModeRegistry.resolve("car").navigate(input);
            assertEquals(Vec3.ZERO, output.force());
            assertTrue(output.torque().lengthSqr() > 0.5D);
        }
    }

    private static ScmControlMode.ControlInput input(
            Vec3 forward,
            Vec3 up,
            Vec3 travelDirection,
            boolean reverse
    ) {
        return new ScmControlMode.ControlInput(
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                forward, up, forward.cross(up),
                travelDirection.scale(5.0D), travelDirection, Vec3.ZERO,
                2.0D, 0.25D, 0.6D, true,
                10.0D, 10.0D, 2.0D, -1.0D,
                true, true, reverse);
    }
}
