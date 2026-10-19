package com.rieno.gadgetsandgizmos.lib.view;

import java.util.ArrayDeque;

// Replay unacknowledged tick samples while previewing the current frame's mouse motion
public final class ViewControlPrediction{
    private final ArrayDeque<Sample> pending = new ArrayDeque<>();
    private ViewRig.State confirmed;
    private long sequence;
    private long acknowledged = -1;

    public ViewControlPrediction(ViewRig.State initial){ confirmed = initial; }

    public ViewRig.State preview(double pan, double tilt, double zoom){
        var rig = new ViewRig();
        rig.restore(confirmed);
        for(var sample : pending) apply(rig, sample.pan(), sample.tilt(), sample.zoom());
        apply(rig, pan, tilt, zoom);
        return rig.state();
    }
    public Sample sample(double pan, double tilt, double zoom){
        var sample = new Sample(++sequence, pan, tilt, zoom);
        pending.addLast(sample);
        return sample;
    }
    public void acknowledge(long sequence, ViewRig.State aim){
        if(sequence <= acknowledged || sequence > this.sequence) return;
        acknowledged = sequence;
        confirmed = aim;
        while(!pending.isEmpty() && pending.peekFirst().sequence() <= sequence) pending.removeFirst();
    }
    private static void apply(ViewRig rig, double pan, double tilt, double zoom){
        rig.turn(pan, tilt);
        rig.zoom(zoom);
    }
    public record Sample(long sequence, double pan, double tilt, double zoom){}
}
