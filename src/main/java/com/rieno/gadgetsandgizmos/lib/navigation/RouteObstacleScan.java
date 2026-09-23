package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.LongSupplier;

// Incrementally inspect actual route segments using a host-owned loaded-world hull probe
public final class RouteObstacleScan {
    private final List<Vec3> points;
    private final LongSupplier clock;
    private int next;
    private int sweep;

    public RouteObstacleScan(List<Vec3> points){
        this(points, System::nanoTime);
    }

    public RouteObstacleScan(List<Vec3> points, LongSupplier clock){
        this.points = List.copyOf(points);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public Hit advance(Probe probe, int maximumSegments, long maximumNanos){
        if(points.size() < 2 || maximumSegments <= 0 || maximumNanos <= 0) return null;
        long started = clock.getAsLong();
        for(int work = 0; work < maximumSegments && clock.getAsLong() - started < maximumNanos; work++){
            int idx = next++;
            Vec3 start = points.get(idx);
            Vec3 end = points.get(idx + 1);
            double distance = start.distanceTo(end);
            double clearance = probe.clearance(start, end);
            if(next >= points.size() - 1){
                next = 0;
                sweep++;
            }
            if(Double.isFinite(clearance) && clearance < distance - 1.0E-4D){
                return new Hit(idx, start.lerp(end, distance <= 1.0E-8D ? 0.0D
                        : Math.max(0.0D, clearance) / distance));
            }
            if(next == 0) break;
        }
        return null;
    }

    public int sweep(){
        return sweep;
    }

    @FunctionalInterface
    public interface Probe {
        double clearance(Vec3 start, Vec3 end);
    }

    public record Hit(int segmentIndex, Vec3 position) {}
}
