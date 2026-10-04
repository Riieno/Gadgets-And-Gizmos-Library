package com.rieno.gadgetsandgizmos.lib.display;

/** Decides when a cached display texture needs a new frame. */
public final class DisplayFrameSchedule {
    private DisplayFrameSchedule() {
    }

    public static boolean shouldRender(boolean hasTexture, boolean modeChanged,
                                       boolean dirty, boolean live,
                                       long now, long lastFrame, long frameIntervalMillis) {
        return !hasTexture || modeChanged || dirty
                || live && now - lastFrame >= frameIntervalMillis;
    }
}
