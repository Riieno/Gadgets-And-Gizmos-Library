package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmSpeedControlTest {
    @Test
    void speedPercentagesArePositiveScalars(){
        assertEquals(0.0D, ScmSpeedControl.percentage(-20.0D));
        assertEquals(0.6D, ScmSpeedControl.percentage(60.0D));
        assertEquals(1.0D, ScmSpeedControl.percentage(100.0D));
        assertEquals(1.0D, ScmSpeedControl.percentage(140.0D));
    }

    @Test
    void clearFiniteScanDoesNotBecomeAnObstacle(){
        assertEquals(28.0D, ScmSpeedControl.scannedSafeSpeed(
                8.0D, 8.0D, 28.0D, 0.5D, 0.35D, 3.0D));
        assertTrue(ScmSpeedControl.scannedSafeSpeed(
                3.0D, 8.0D, 28.0D, 0.5D, 0.35D, 3.0D) < 28.0D);
    }

    @Test
    void automaticPropulsionSurvivesNormalizationAndExplicitZeroStillStops(){
        assertEquals(-1.0D, ScmSpeedControl.propulsionRequest(-1.0D));
        assertEquals(-1.0D, ScmSpeedControl.propulsionRequest(Double.NaN));
        assertEquals(0.0D, ScmSpeedControl.propulsionRequest(0.0D));
        assertEquals(0.5D, ScmSpeedControl.propulsionRequest(0.5D));
        assertEquals(1.0D, ScmSpeedControl.propulsionRequest(2.0D));
    }
    @Test
    void accelerationTapersAsTheVehicleReachesItsPermittedSpeed() {
        ScmSpeedControl.Demand farBelow = ScmSpeedControl.plan(
                new ScmSpeedControl.Request(0.0D, 10.0D, 10.0D, 1.0D, 0.35D));
        ScmSpeedControl.Demand approaching = ScmSpeedControl.plan(
                new ScmSpeedControl.Request(9.5D, 10.0D, 10.0D, 1.0D, 0.35D));
        ScmSpeedControl.Demand reached = ScmSpeedControl.plan(
                new ScmSpeedControl.Request(10.0D, 10.0D, 10.0D, 1.0D, 0.35D));

        assertEquals(1.0D, farBelow.acceleration(), 1.0E-6D);
        assertTrue(approaching.acceleration() < farBelow.acceleration());
        assertEquals(0.0D, reached.acceleration(), 1.0E-6D);
    }

    @Test
    void routeSpeedGroupsStayPoweredAcrossCruiseTelemetryChanges(){
        for(double speed : new double[]{0.0D, 9.5D, 10.0D}){
            ScmSpeedControl.Demand demand = ScmSpeedControl.planSpeedGroup(
                    new ScmSpeedControl.Request(speed, 10.0D, 10.0D, 0.8D, 0.35D));
            assertEquals(0.8D, demand.acceleration(), 1.0E-9D);
            assertEquals(0.0D, demand.deceleration());
            assertEquals(0.0D, demand.brake());
            assertTrue(demand.exclusive());
        }
    }

    @Test
    void routeSpeedGroupsTrackSafetyCapsWithoutReversingTheirSignal(){
        for(double cap : new double[]{10.0D, 5.0D, 0.25D}){
            ScmSpeedControl.Demand demand = ScmSpeedControl.planSpeedGroup(
                    new ScmSpeedControl.Request(cap, cap, 10.0D, 0.6D, 0.35D));
            assertEquals(0.6D * cap / 10.0D, demand.acceleration(), 1.0E-9D);
            assertTrue(demand.acceleration() > 0.0D && demand.exclusive());
        }
        ScmSpeedControl.Demand stopped = ScmSpeedControl.planSpeedGroup(
                new ScmSpeedControl.Request(10.0D, 0.0D, 10.0D, 1.0D, 0.35D));
        assertEquals(0.0D, stopped.acceleration());
        assertTrue(stopped.brake() > 0.0D && stopped.exclusive());
        assertEquals(ScmSpeedControl.Demand.IDLE, ScmSpeedControl.planSpeedGroup(
                new ScmSpeedControl.Request(0.0D, 10.0D, 10.0D, 0.0D, 0.35D)));
    }

    @Test
    void routeSpeedGroupsUseAccelerationAndBrakeTogetherForOverspeed(){
        ScmSpeedControl.Demand demand = ScmSpeedControl.planSpeedGroup(
                new ScmSpeedControl.Request(12.0D, 5.0D, 20.0D, 0.8D, 0.35D));
        assertEquals(0.2D, demand.acceleration(), 1.0E-9D);
        assertEquals(0.0D, demand.deceleration());
        assertTrue(demand.brake() > 0.0D);
        assertEquals(0.2D, ScmSpeedControl.accelerationSetpoint(
                demand.acceleration(), 0.0D), 1.0E-9D);
    }

    @Test
    void accelerationSignalsArePositiveOnlyAndYieldToSpeedReduction(){
        assertEquals(0.0D, ScmSpeedControl.accelerationSignal(-1.0D, 0.0D, 0.0D));
        assertEquals(0.0D, ScmSpeedControl.accelerationSignal(Double.NaN, 0.0D, 0.0D));
        assertEquals(1.0D, ScmSpeedControl.accelerationSignal(2.0D, 0.0D, 0.0D));
        assertEquals(0.6D, ScmSpeedControl.accelerationSignal(0.6D, 0.0D, 0.0D));
        assertEquals(0.0D, ScmSpeedControl.accelerationSignal(0.6D, 0.2D, 0.0D));
        assertEquals(0.0D, ScmSpeedControl.accelerationSignal(0.6D, 0.0D, 0.2D));
    }
}
