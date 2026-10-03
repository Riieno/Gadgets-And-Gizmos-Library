package com.rieno.gadgetsandgizmos.lib.zipline;

import net.minecraft.world.phys.Vec3;

// A short, distance-driven connector between two rope strands.
public record ZiplineHandoffSpline(Vec3 start, Vec3 startTangent, Vec3 end, Vec3 endTangent) {
    public Vec3 sample(double fraction) {
        double t = Math.clamp(fraction, 0.0D, 1.0D);
        double gap = start.distanceTo(end);
        Vec3 first = start.add(unit(startTangent).scale(Math.min(0.35D, gap * 0.35D)));
        Vec3 second = end.subtract(unit(endTangent).scale(Math.min(0.35D, gap * 0.35D)));
        double reverse = 1.0D - t;
        return start.scale(reverse * reverse * reverse)
                .add(first.scale(3.0D * reverse * reverse * t))
                .add(second.scale(3.0D * reverse * t * t))
                .add(end.scale(t * t * t));
    }

    public Vec3 tangent(double fraction) {
        double t = Math.clamp(fraction, 0.0D, 1.0D);
        Vec3 direction = sample(Math.min(1.0D, t + 0.01D))
                .subtract(sample(Math.max(0.0D, t - 0.01D)));
        return unit(direction);
    }

    public float length() {
        Vec3 previous = start;
        double total = 0.0D;
        for (int index = 1; index <= 16; index++) {
            Vec3 point = sample(index / 16.0D);
            total += point.distanceTo(previous);
            previous = point;
        }
        return (float) Math.max(0.05D, total);
    }

    private static Vec3 unit(Vec3 vector) {
        return vector.lengthSqr() < 1.0E-8D ? Vec3.ZERO : vector.normalize();
    }
}
