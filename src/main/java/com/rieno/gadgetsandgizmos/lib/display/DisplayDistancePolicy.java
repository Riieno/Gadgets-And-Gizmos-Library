package com.rieno.gadgetsandgizmos.lib.display;

// Separate live updates, retained previews and complete distance culling
public record DisplayDistancePolicy(double liveRange, double renderRange){
    public enum Mode{ LIVE, STATIC, HIDDEN }
    public DisplayDistancePolicy{
        if(!Double.isFinite(liveRange) || !Double.isFinite(renderRange) || liveRange < 0 || renderRange < liveRange)
            throw new IllegalArgumentException("Display ranges must be finite, nonnegative and ordered");
    }
    public Mode mode(double distanceSquared, boolean enabled){
        if(!enabled || !Double.isFinite(distanceSquared) || distanceSquared < 0 || distanceSquared > renderRange * renderRange) return Mode.HIDDEN;
        return distanceSquared <= liveRange * liveRange ? Mode.LIVE : Mode.STATIC;
    }
}
