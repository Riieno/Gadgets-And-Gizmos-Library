package com.rieno.gadgetsandgizmos.lib.view;

// Supply a separate first-person aim without changing replicated state or captured feeds
public interface PredictedViewSource extends ViewSource{
    ViewRig.State viewAim();
    ViewPose viewPose(float partialTick, ViewRig.State aim);

    // Preview source-specific pointer mapping without mutating replicated camera state
    default ViewRig.State predictViewAim(ViewRig.State aim, double pan, double tilt, double zoom){
        return ViewControlPrediction.applyInput(aim, pan, tilt, zoom);
    }
}
