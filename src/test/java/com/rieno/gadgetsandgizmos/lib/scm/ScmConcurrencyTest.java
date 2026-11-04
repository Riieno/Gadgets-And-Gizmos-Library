package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ScmConcurrencyTest{
    // Saturate workers and churn caches while checking the original bit patterns on every caller
    @Test
    void workerSaturationRetainsAllCapturedControlOutputs() throws Exception{
        try(var callers = Executors.newFixedThreadPool(8)){
            var jobs = new ArrayList<java.util.concurrent.Future<?>>();
            for(int idx = 0; idx < 24; idx++){
                jobs.add(callers.submit(() -> {
                    var regression = new ScmDeterminismTest();
                    regression.precisionMatchesCapturedOutputs();
                    regression.adaptiveMatchesCapturedOutputs();
                    regression.articulatedMatchesCapturedOutputs();
                }));
            }
            for(var job : jobs) job.get(60, TimeUnit.SECONDS);
        }
    }

    @Test
    void retainedControlsAndPublishedResultsHaveIndependentStorage(){
        var units = ScmDeterminismTest.units(new java.util.Random(91), 32);
        double[] previous = new double[32];
        java.util.Arrays.fill(previous, .25);
        var first = ScmPrecisionAllocator.allocate(units, new net.minecraft.world.phys.Vec3(3, 4, 5),
                net.minecraft.world.phys.Vec3.ZERO, previous);
        double[] res = first.controls();
        res[0] = 123;
        assertNotEquals(123, first.controls()[0]);
        for(double val : previous) assertEquals(.25, val);
    }

    // Share one model deliberately while signed solves and axis-cache eviction compete
    @Test
    void sharedModelKeepsExactFeedbackAcrossConcurrentQueries() throws Exception{
        var random = new java.util.Random(7521);
        var actuators = new ArrayList<ScmAdaptiveStateModel.Actuator>();
        for(int idx = 0; idx < 32; idx++){
            actuators.add(new ScmAdaptiveStateModel.Actuator(
                    new net.minecraft.world.phys.Vec3(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5),
                    new net.minecraft.world.phys.Vec3(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5), .4));
        }
        var model = ScmAdaptiveStateModel.sample(actuators, .05, .1, .02, 2);
        var axes = new ArrayList<net.minecraft.world.phys.Vec3>();
        var expected = new ArrayList<ScmAdaptiveStateModel.Response>();
        for(int idx = 0; idx < 256; idx++){
            var axis = new net.minecraft.world.phys.Vec3(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5);
            axes.add(axis);
            expected.add(model.feedback(axis, (idx & 1) == 0, .5, -.1));
        }
        try(var callers = Executors.newFixedThreadPool(8)){
            var jobs = new ArrayList<java.util.concurrent.Future<?>>();
            for(int idx = 0; idx < 512; idx++){
                int query = idx % axes.size();
                jobs.add(callers.submit(() -> assertEquals(expected.get(query),
                        model.feedback(axes.get(query), (query & 1) == 0, .5, -.1))));
            }
            for(var job : jobs) job.get(30, TimeUnit.SECONDS);
        }
    }
}
