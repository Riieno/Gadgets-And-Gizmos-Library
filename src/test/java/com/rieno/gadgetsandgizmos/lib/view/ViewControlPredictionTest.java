package com.rieno.gadgetsandgizmos.lib.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewControlPredictionTest{
    @Test void mouseMotionChangesEveryFrameBetweenTickSamples(){
        var prediction = new ViewControlPrediction(new ViewRig.State(0, -90, 70));
        for(int frame = 1; frame <= 12; frame++){
            assertEquals(frame * .5, prediction.preview(frame * .5, 0, 0).pan(), 1e-10);
        }
        var sample = prediction.sample(6, 0, 0);
        assertEquals(7, prediction.preview(1, 0, 0).pan(), 1e-10);
        prediction.acknowledge(sample.sequence(), new ViewRig.State(6, -90, 70));
        assertEquals(7, prediction.preview(1, 0, 0).pan(), 1e-10);
    }
    @Test void acknowledgementsReplayOnlyNewerInputAndCorrectRejectedMotion(){
        var prediction = new ViewControlPrediction(new ViewRig.State(0, -90, 70));
        var first = prediction.sample(10, 2, 1);
        var second = prediction.sample(20, 3, 0);
        prediction.acknowledge(first.sequence(), new ViewRig.State(10, -88, 65));
        assertEquals(new ViewRig.State(30, -85, 65), prediction.preview(0, 0, 0));
        prediction.acknowledge(second.sequence(), new ViewRig.State(25, -85, 65));
        assertEquals(27, prediction.preview(2, 0, 0).pan());
        prediction.acknowledge(first.sequence(), new ViewRig.State(-80, 0, 100));
        assertEquals(27, prediction.preview(2, 0, 0).pan());
    }
    @Test void previewUsesServerLimitsAndWrapsWithoutChangingTheAuthoritativeAim(){
        var initial = new ViewRig.State(179, 130, 70);
        var prediction = new ViewControlPrediction(initial);
        assertEquals(new ViewRig.State(-136, 135, 20), prediction.preview(100, 100, 100));
        assertEquals(initial, prediction.preview(0, 0, 0));
        prediction.sample(10, -10, -1);
        assertEquals(new ViewRig.State(-166, 118, 75), prediction.preview(5, -2, 0));
    }
}
