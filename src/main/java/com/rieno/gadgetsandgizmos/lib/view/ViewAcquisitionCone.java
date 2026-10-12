package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

// Sweep a widening detection radius along an observer's forward axis
public record ViewAcquisitionCone(Vec3 origin, Vec3 forward, double range, double radius, double halfAngle){
    public ViewAcquisitionCone{
        if(origin == null || forward == null || !finite(origin) || !finite(forward)
                || !Double.isFinite(forward.lengthSqr()) || forward.lengthSqr() < 1.0E-12 || !Double.isFinite(range) || range < 0
                || !Double.isFinite(radius) || radius < 0 || !Double.isFinite(halfAngle)
                || halfAngle < 0 || halfAngle > 60) throw new IllegalArgumentException("Invalid acquisition cone");
        forward = forward.normalize();
    }

    // Query only bodies intersecting a conservative world envelope
    public AABB bounds(){
        return new AABB(origin, origin.add(forward.scale(range))).inflate(radius + slope() * range);
    }

    // Find a point on target bounds inside the widened sweep, including edges whose centre misses it
    public @Nullable Vec3 point(AABB bounds){
        if(range <= 0 || ViewTargetLock.distanceSquared(origin, bounds) > range * range) return null;
        Vec3 center = bounds.getCenter().subtract(origin);
        double extent = (Math.abs(forward.x) * bounds.getXsize() + Math.abs(forward.y) * bounds.getYsize()
                + Math.abs(forward.z) * bounds.getZsize()) * .5;
        double ahead = center.dot(forward);
        if(ahead + extent < 0 || ahead - extent > range) return null;

        double[] starts = {origin.x, origin.y, origin.z};
        double[] dirs = {forward.x, forward.y, forward.z};
        double[] mins = {bounds.minX, bounds.minY, bounds.minZ};
        double[] maxs = {bounds.maxX, bounds.maxY, bounds.maxZ};
        double[] cuts = new double[8];
        cuts[0] = 0;
        cuts[1] = range;
        int count = 2;
        for(int idx = 0; idx < 3; idx++){
            if(Math.abs(dirs[idx]) < 1.0E-12) continue;
            for(double edge : new double[]{mins[idx], maxs[idx]}){
                double t = (edge - starts[idx]) / dirs[idx];
                if(t > 0 && t < range) cuts[count++] = t;
            }
        }
        Arrays.sort(cuts, 0, count);
        double best = Double.POSITIVE_INFINITY;
        Vec3 res = null;
        double slope = slope();
        for(int idx = 1; idx < count; idx++){
            double left = cuts[idx - 1];
            double right = cuts[idx];
            double middle = (left + right) * .5;
            double a = -slope * slope;
            double b = -2 * radius * slope;
            for(int axis = 0; axis < 3; axis++){
                double val = starts[axis] + dirs[axis] * middle;
                if(val >= mins[axis] && val <= maxs[axis]) continue;
                double delta = starts[axis] - (val < mins[axis] ? mins[axis] : maxs[axis]);
                a += dirs[axis] * dirs[axis];
                b += 2 * delta * dirs[axis];
            }
            double vertex = a > 1.0E-12 ? Math.clamp(-b / (2 * a), left, right) : left;
            for(double t : new double[]{left, right, vertex}){
                Vec3 axis = origin.add(forward.scale(t));
                Vec3 point = new Vec3(Math.clamp(axis.x, bounds.minX, bounds.maxX),
                        Math.clamp(axis.y, bounds.minY, bounds.maxY), Math.clamp(axis.z, bounds.minZ, bounds.maxZ));
                double reach = radius + slope * t;
                double score = axis.distanceToSqr(point) - reach * reach;
                double distance = origin.distanceToSqr(point);
                if(score <= 1.0E-9 && distance <= range * range && distance < best){
                    best = distance;
                    res = point;
                }
            }
        }
        return res;
    }

    // Keep final geometric hits in the same volume as their broad phase candidates
    public boolean contains(Vec3 point){ return point(new AABB(point, point)) != null; }

    private double slope(){ return Math.tan(Math.toRadians(halfAngle)); }
    private static boolean finite(Vec3 val){ return Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z); }
}
