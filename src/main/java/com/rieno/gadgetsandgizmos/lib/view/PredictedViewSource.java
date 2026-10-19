package com.rieno.gadgetsandgizmos.lib.view;

// Supply a separate first-person aim without changing replicated state or captured feeds
public interface PredictedViewSource extends ViewSource{
    ViewRig.State viewAim();
    ViewPose viewPose(float partialTick, ViewRig.State aim);
}
