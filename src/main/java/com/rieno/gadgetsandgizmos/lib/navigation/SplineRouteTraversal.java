package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Optional;

// Capture a directed route at its entry and advance its legs in order
public final class SplineRouteTraversal{
    private final WaypointSpline spline;
    private boolean captured;
    private boolean entryAcquired;
    private int segment;
    private double fraction;

    public SplineRouteTraversal(WaypointSpline spline, boolean captureAtEntry){
        if(spline == null || spline.segments().isEmpty()) throw new IllegalArgumentException("A route needs two distinct points");
        this.spline = spline;
        captured = !captureAtEntry;
    }

    public WaypointSpline spline(){ return spline; }
    public boolean captured(){ return captured; }
    public boolean entryAcquired(){ return entryAcquired; }
    public int segment(){ return segment; }
    public double fraction(){ return fraction; }
    public Vec3 entry(){ return spline.waypoints().getFirst(); }

    // Begin lane alignment when any physical hull member overlaps the assigned entry
    public static boolean entryOverlaps(Vec3 entry, java.util.Collection<AABB> bounds){
        return entry != null && bounds != null && bounds.stream().anyMatch(box ->
                box != null && box.inflate(0.05D).contains(entry));
    }

    // Retain hull entry while the tracked connector moves into the lane
    public boolean acquireEntry(java.util.Collection<AABB> bounds){
        entryAcquired |= entryOverlaps(entry(), bounds);
        return entryAcquired;
    }


    // Carry the entire lane with a rigid frame without losing captured or ordered progress
    public SplineRouteTraversal transformed(java.util.function.UnaryOperator<Vec3> transform){
        var res = new SplineRouteTraversal(WaypointSpline.of(spline.waypoints().stream().map(transform).toList()), !captured);
        res.entryAcquired = entryAcquired;
        if(res.spline.segments().size() != spline.segments().size())
            throw new IllegalArgumentException("A route transform must preserve its legs");
        res.segment = segment;
        res.fraction = fraction;
        return res;
    }

    // Replace only unvisited travel; a captured ship must retain the same directed prefix
    public Optional<SplineRouteTraversal> withContinuation(WaypointSpline replacement, double tolerance){
        if(replacement == null || replacement.isEmpty()) return Optional.empty();
        if(replacement.waypoints().equals(spline.waypoints())) return Optional.of(this);
        var res = new SplineRouteTraversal(replacement, !captured);
        if(entryAcquired && replacement.waypoints().getFirst().distanceTo(entry()) > Math.max(0.05D, tolerance)) return Optional.empty();
        res.entryAcquired = entryAcquired;
        if(!captured) return Optional.of(res);
        double progress = spline.projectSegment(spline.segments().get(segment).pointAtFraction(fraction), segment, fraction).distanceAlongRoute();
        double radius = Math.max(0.05D, tolerance);
        if(replacement.length() + 1.0E-6D < progress) return Optional.empty();
        for(double distance = 0; distance <= progress + 0.5D; distance += 0.5D){
            double cursor = Math.min(progress, distance);
            var prev = spline.pointAtDistance(cursor);
            var next = replacement.pointAtDistance(cursor);
            if(prev.position().distanceTo(next.position()) > radius || prev.tangent().normalize().dot(next.tangent().normalize()) < 0.5D)
                return Optional.empty();
        }
        var cursor = replacement.pointAtDistance(progress);
        res.segment = cursor.segmentIndex();
        res.fraction = cursor.fraction();
        return Optional.of(res);
    }

    // Begin facing the lane before the tracked point reaches its entry
    public boolean aligningAtEntry(Vec3 position){ return !captured && position.distanceTo(entry()) <= 20.0D; }

    // Airships capture only after turning toward the lane, without changing their physical pose
    public WaypointSpline.TrackingTarget update(Vec3 position, Vec3 forward, double captureRadius, double lookahead){
        if(!captured && headingSpeedLimit(forward, this.forward(0.0D), 1.0D) < 0.9D)
            return WaypointSpline.TrackingTarget.notFound();
        return update(position, captureRadius, lookahead);
    }

    // Slow lane travel while the flight controls turn naturally into its heading
    public static double headingSpeedLimit(Vec3 forward, Vec3 heading, double speed){
        Vec3 flat = new Vec3(heading.x, 0, heading.z);
        Vec3 facing = new Vec3(forward.x, 0, forward.z).normalize();
        double alignment = flat.lengthSqr() < 1.0E-12D ? 1 : facing.dot(flat.normalize());
        return Math.max(0, speed) * Math.max(0, Math.min(1, (alignment - 0.25D) / 0.65D));
    }

    // Sweep from the physical point back to the retained leg before its ordered continuation
    public java.util.List<Vec3> remainingControls(Vec3 position){
        var projection = spline.projectSegment(position, segment, fraction);
        java.util.List<Vec3> res = new java.util.ArrayList<>();
        res.add(position);
        res.add(projection.position());
        res.addAll(spline.waypoints().subList(segment + 1, spline.waypoints().size()));
        return WaypointSpline.of(res).waypoints();
    }

    // Measure unvisited travel through bends without projecting onto a later crossing
    public double remainingDistance(Vec3 position){
        if(!captured) return position.distanceTo(entry()) + spline.length();
        var projection = spline.projectSegment(position, segment, fraction);
        return Math.max(0.0D, spline.length() - projection.distanceAlongRoute()) + projection.distance();
    }

    // Complete within the stopping tolerance without skipping unvisited bends or crossings
    public boolean arrived(Vec3 position, double tolerance){
        if(!captured || position == null) return false;
        double radius = Double.isFinite(tolerance) ? Math.max(0, tolerance) : 0;
        return remainingDistance(position) <= radius;
    }

    // Publish only a bounded future corridor from the retained cursor
    public java.util.List<Vec3> trafficControls(Vec3 position, double lookahead){
        var projection = spline.projectSegment(position, segment, fraction);
        double range = Double.isFinite(lookahead) ? Math.max(0.5D, Math.min(256.0D, lookahead)) : 64.0D;
        double end = Math.min(spline.length(), projection.distanceAlongRoute() + range);
        java.util.List<Vec3> res = new java.util.ArrayList<>();
        res.add(position);
        res.add(projection.position());
        for(double distance = projection.distanceAlongRoute() + 1.0D; distance < end; distance += 1.0D)
            res.add(spline.pointAtDistance(distance).position());
        res.add(spline.pointAtDistance(end).position());
        return WaypointSpline.of(res).waypoints();
    }

    // Do not acquire a later crossing or skip an unvisited leg
    public WaypointSpline.TrackingTarget update(Vec3 position, double captureRadius, double lookahead){
        double radius = Math.max(0.1D, captureRadius);
        if(!captured){
            if(position.distanceTo(entry()) > radius) return WaypointSpline.TrackingTarget.notFound();
            captured = true;
        }
        WaypointSpline.Projection projection = spline.projectSegment(position, segment, fraction);
        fraction = Math.max(fraction, projection.fraction());
        while(segment < spline.segments().size() - 1 && fraction >= 0.97D && passedLeg(position, radius)){
            segment++;
            fraction = 0.0D;
            projection = spline.projectSegment(position, segment, fraction);
            fraction = Math.max(fraction, projection.fraction());
        }
        return spline.trackingTarget(projection, Math.max(0.1D, lookahead));
    }

    // An overshot short leg can advance through its continuation without acquiring a later crossing
    private boolean passedLeg(Vec3 position, double radius){
        var leg = spline.segments().get(segment);
        if(position.distanceTo(leg.end()) <= radius) return true;
        Vec3 delta = position.subtract(leg.end()), forward = leg.tangentAtFraction(1).normalize();
        double along = delta.dot(forward);
        if(along < 0 || delta.subtract(forward.scale(along)).length() > radius) return false;
        var next = spline.segments().get(segment + 1);
        var projection = spline.projectSegment(position, segment + 1, 0);
        return next.tangentAtFraction(0).normalize().dot(forward) > 0.5D
                && (projection.distance() <= radius || projection.fraction() >= 0.97D
                && next.tangentAtFraction(1).normalize().dot(forward) > 0.9D);
    }

    // Face along ordered travel instead of yawing towards sideways position corrections
    public Vec3 forward(double lookahead){
        WaypointSpline.Segment leg = spline.segments().get(segment);
        double distance = spline.segments().subList(0, segment).stream().mapToDouble(WaypointSpline.Segment::length).sum()
                + leg.lengthToFraction(fraction);
        return spline.pointAtDistance(distance + Math.max(0.0D, lookahead)).tangent();
    }

    // Retain ordered progress when the owning vehicle unloads
    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.put("Points", WaypointSplineData.points(spline.waypoints()));
        tag.putBoolean("Captured", captured);
        if(entryAcquired) tag.putBoolean("EntryAcquired", true);
        tag.putInt("Segment", segment);
        tag.putDouble("Fraction", fraction);
        return tag;
    }

    public static Optional<SplineRouteTraversal> fromTag(CompoundTag tag){
        WaypointSpline spline = WaypointSpline.of(WaypointSplineData.readPoints(tag.getList("Points", Tag.TAG_COMPOUND), 131072));
        if(spline.isEmpty()) return Optional.empty();
        SplineRouteTraversal traversal = new SplineRouteTraversal(spline, !tag.getBoolean("Captured"));
        traversal.entryAcquired = tag.getBoolean("EntryAcquired");
        traversal.segment = Math.max(0, Math.min(spline.segments().size() - 1, tag.getInt("Segment")));
        double fraction = tag.getDouble("Fraction");
        traversal.fraction = Double.isFinite(fraction) ? Math.max(0, Math.min(1, fraction)) : 0;
        return Optional.of(traversal);
    }

    public boolean finalLeg(){ return segment == spline.segments().size() - 1; }
}
