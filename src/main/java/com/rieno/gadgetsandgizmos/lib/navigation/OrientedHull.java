package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Project hull points onto a signed craft frame for face, edge and corner targeting. */
public final class OrientedHull {
    private OrientedHull(){}

    /** Union corresponding hull pieces through a heading turn, including intermediate corner extents. */
    public static List<AABB> sweptHeadingBounds(Vec3 from, Vec3 to, Function<Vec3, List<AABB>> poseBounds){
        if(!finite(from) || !finite(to)) throw new IllegalArgumentException("Finite headings required");
        List<AABB> bounds = new ArrayList<>(poseBounds.apply(from));
        for(int sample = 1; sample <= 4; sample++){
            Vec3 heading = from.lerp(to, sample / 4.0D);
            heading = heading.lengthSqr() < 1e-8 ? to : heading.normalize();
            List<AABB> rotated = poseBounds.apply(heading);
            if(rotated.size() != bounds.size()) throw new IllegalArgumentException("Hull topology changed during turn query");
            for(int index = 0; index < bounds.size(); index++){
                bounds.set(index, bounds.get(index).minmax(rotated.get(index)));
            }
        }
        return List.copyOf(bounds);
    }

    /** Select minimum (-1), middle (0) or maximum (+1) on each orthonormal frame axis. */
    public static Vec3 targetPoint(Collection<Vec3> points, Vec3 right, Vec3 up, Vec3 forward,
                                   int rightSelector, int upSelector, int forwardSelector, Vec3 fallback){
        Vec3 safeFallback = finite(fallback) ? fallback : Vec3.ZERO;
        if(points == null || points.isEmpty() || !finite(right) || !finite(up) || !finite(forward)){
            return safeFallback;
        }
        right = right.normalize();
        up = up.normalize();
        forward = forward.normalize();
        if(right.lengthSqr() < 0.5D || up.lengthSqr() < 0.5D || forward.lengthSqr() < 0.5D
                || Math.abs(right.dot(up)) > 1.0E-6D || Math.abs(right.dot(forward)) > 1.0E-6D
                || Math.abs(up.dot(forward)) > 1.0E-6D){
            return safeFallback;
        }
        Vec3[] axes = {right, up, forward};
        int[] selectors = {rightSelector, upSelector, forwardSelector};
        double[] minima = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] maxima = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for(Vec3 point : points){
            if(!finite(point)) continue;
            for(int index = 0; index < axes.length; index++){
                double projection = point.dot(axes[index]);
                minima[index] = Math.min(minima[index], projection);
                maxima[index] = Math.max(maxima[index], projection);
            }
        }
        Vec3 result = Vec3.ZERO;
        for(int index = 0; index < axes.length; index++){
            if(!Double.isFinite(minima[index]) || !Double.isFinite(maxima[index])) return safeFallback;
            double position = selectors[index] < 0 ? minima[index] : selectors[index] > 0 ? maxima[index]
                    : (minima[index] + maxima[index]) * 0.5D;
            result = result.add(axes[index].scale(position));
        }
        return result;
    }

    private static boolean finite(Vec3 value){
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
