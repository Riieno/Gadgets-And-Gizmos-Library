package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmFlightPowerTest{
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    @Test
    void regulatedLiftUsesAFullDirectionSelector(){
        assertEquals(1.0D, ScmControlAxes.liftSelector(Vec3.ZERO, UP.scale(0.2D), UP, true));
        assertEquals(-1.0D, ScmControlAxes.liftSelector(Vec3.ZERO, UP.scale(-0.2D), UP, true));
        assertEquals(0.35D, ScmControlAxes.liftSelector(UP.scale(0.35D), UP, UP, false));
    }

    @Test
    void takeoffAndMovingFlightRetainEnginePowerDespiteTravelBraking(){
        for(String command : new String[]{"ship_navigate", "ship_follow", "ship_climb", "ship_hover"}){
            double power = ScmCommandRouting.sustainingAccelerationPower(command, -1.0D, 1.0D);
            for(double speed : new double[]{0.0D, 0.1D, 8.0D, 12.0D}){
                for(double cap : new double[]{0.0D, 4.0D, 8.0D}){
                    var travel = ScmSpeedControl.plan(new ScmSpeedControl.Request(
                            speed, cap, 8.0D, 1.0D, 0.35D));
                    assertEquals(1.0D, ScmSpeedControl.accelerationSignal(
                            travel.acceleration(), travel.deceleration(), travel.brake(), power));
                }
            }
        }
    }

    @Test
    void authoredThrottleAndClimbStrengthRemainPositiveOnly(){
        assertEquals(0.4D, ScmCommandRouting.sustainingAccelerationPower("ship_follow", 0.4D, 1.0D));
        assertEquals(0.3D, ScmCommandRouting.sustainingAccelerationPower("ship_climb", -1.0D, 0.3D));
        assertEquals(0.0D, ScmCommandRouting.sustainingAccelerationPower("ship_navigate", 0.0D, 1.0D));
        assertEquals(0.0D, ScmCommandRouting.sustainingAccelerationPower("ship_climb", -1.0D, 0.0D));
        assertEquals(0.0D, ScmCommandRouting.sustainingAccelerationPower("ship_stop", -1.0D, 1.0D));
        assertEquals(0.0D, ScmCommandRouting.sustainingAccelerationPower("ship_yaw", -1.0D, 1.0D));
        assertEquals(0.0D, ScmCommandRouting.sustainingAccelerationPower(null, -1.0D, 1.0D));
        for(double power : new double[]{-1.0D, Double.NaN, Double.POSITIVE_INFINITY}){
            assertEquals(0.0D, ScmSpeedControl.accelerationSignal(-1.0D, 1.0D, 1.0D, power));
        }
        assertEquals(1.0D, ScmSpeedControl.accelerationSignal(0.0D, 1.0D, 1.0D, 2.0D));
        assertEquals(0.4D, ScmSpeedControl.accelerationSignal(1.0D, 1.0D, 0.0D, 0.4D));
    }

    @Test
    void groundTravelStillYieldsPowerToBraking(){
        assertEquals(0.0D, ScmSpeedControl.accelerationSignal(1.0D, 0.1D, 0.0D));
        assertEquals(0.0D, ScmSpeedControl.accelerationSignal(1.0D, 0.0D, 1.0D));
        assertEquals(0.0D, ScmSpeedControl.accelerationSignal(1.0D, 0.0D, 1.0D, 0.0D));
    }

    @Test
    void hoverAndSmallClimbKeepLiftWithoutChangingTravelOrDescent(){
        Vec3 forward = new Vec3(0.6D, 0.0D, 0.2D);
        Vec3 hold = UP.scale(0.4D);
        assertEquals(forward.add(hold), ScmControlAxes.withLiftSupport(forward, hold, UP));
        assertEquals(forward.add(hold), ScmControlAxes.withLiftSupport(
                forward.add(UP.scale(0.01D)), hold, UP));
        Vec3 climb = forward.add(UP.scale(0.8D));
        Vec3 descent = forward.add(UP.scale(-0.2D));
        assertEquals(climb, ScmControlAxes.withLiftSupport(climb, hold, UP));
        assertEquals(descent, ScmControlAxes.withLiftSupport(descent, hold, UP));
        assertEquals(forward, ScmControlAxes.withLiftSupport(forward, hold, Vec3.ZERO));
        assertEquals(Vec3.ZERO, ScmControlAxes.withLiftSupport(null, null, UP));
    }

    @Test
    void groundedAndFallingAirshipCanApplyUpwardControlDuringAZeroTravelCap(){
        Vec3 forward = new Vec3(0.0D, 0.0D, 1.0D);
        for(Vec3 velocity : new Vec3[]{Vec3.ZERO, UP.scale(-0.1D), forward.scale(8.0D)}){
            var input = new ScmControlMode.ControlInput(Vec3.ZERO, velocity, Vec3.ZERO,
                    forward, UP, forward.cross(UP), UP.scale(20.0D), UP, Vec3.ZERO,
                    8.0D, 0.75D, 0.6D, true, 0.0D, 0.0D, 0.0D,
                    1.0D, true, true, false);
            var output = ScmControlModeRegistry.resolve("airship").navigate(input);
            Vec3 lift = ScmControlAxes.withLiftSupport(output.force(), UP.scale(0.4D), UP);
            assertTrue(lift.dot(UP) >= 0.4D);
            var travel = ScmSpeedControl.plan(new ScmSpeedControl.Request(
                    velocity.length(), 0.0D, 8.0D, 1.0D, 0.35D));
            assertEquals(1.0D, ScmSpeedControl.accelerationSignal(output.driveStrength(),
                    travel.deceleration(), travel.brake(),
                    ScmCommandRouting.sustainingAccelerationPower("ship_navigate", -1.0D, 1.0D)));
        }
    }
}
