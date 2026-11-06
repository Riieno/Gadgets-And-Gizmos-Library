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
        return preview(pan, tilt, zoom, ViewControlPrediction::applyInput);
    }
    // Replay pending samples through the same input mapping used by their source
    public ViewRig.State preview(double pan, double tilt, double zoom, InputTransform transform){
        ViewRig.State aim = confirmed;
        for(var sample : pending) aim = transform.apply(aim, sample.pan(), sample.tilt(), sample.zoom());
        return transform.apply(aim, pan, tilt, zoom);
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
    // Retain the standard scalar mapping for sources without a custom viewing frame
    public static ViewRig.State applyInput(ViewRig.State aim, double pan, double tilt, double zoom){
        var rig = new ViewRig();
        rig.restore(aim);
        rig.turn(pan, tilt);
        rig.zoom(zoom);
        return rig.state();
    }
    // Transform one input sample without changing the source or the supplied aim
    @FunctionalInterface
    public interface InputTransform{
        ViewRig.State apply(ViewRig.State aim, double pan, double tilt, double zoom);
    }
    public record Sample(long sequence, double pan, double tilt, double zoom){}
}
