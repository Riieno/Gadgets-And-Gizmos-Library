package com.rieno.gadgetsandgizmos.lib.client.render;

// Let a covering client screen skip the main world render without pausing simulation
public interface WorldRenderControl{
    boolean suppressWorldRendering();

    static boolean shouldRenderWorld(boolean requested, Object screen){
        return requested && (!(screen instanceof WorldRenderControl control) || !control.suppressWorldRendering());
    }
}
