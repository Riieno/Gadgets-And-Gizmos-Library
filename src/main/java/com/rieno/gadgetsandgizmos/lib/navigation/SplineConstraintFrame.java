package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3d;
import org.joml.Quaterniond;

import java.util.List;

// Resolve route frames and safe capture without changing the vehicle's pose
public final class SplineConstraintFrame{
    private static final double HULL_CAPTURE_SAMPLE_SPACING = 0.25D;
    private static final int HULL_CAPTURE_MIN_SAMPLES = 16;
    private static final int HULL_CAPTURE_MAX_SAMPLES = 256;
    private static final int HULL_CAPTURE_REFINEMENT_STEPS = 10;

    // Initialize the spline frame helper
    private SplineConstraintFrame(){}

    // Get the route tangent on the constrained axes
    public static Vec3 tangent(WaypointSpline.Projection projection, AxisPolicy axes){
        if(projection == null || !projection.found()) return Vec3.ZERO;
        Vec3 dir = policy(axes).filter(projection.tangent());
        return dir.lengthSqr() <= 1.0E-12D ? Vec3.ZERO : dir.normalize();
    }

    // Anticipate half a physics step so a body-owned frame follows curves without centreline pull
    public static Vec3 anticipatedTangent(
            WaypointSpline spline,
            WaypointSpline.Projection projection,
            Vec3 velocity,
            AxisPolicy axes,
            double timeStep
    ){
        Vec3 current = tangent(projection, axes);
        if(spline == null || spline.isEmpty() || current.lengthSqr() <= 1.0E-12D
                || !finite(velocity) || !Double.isFinite(timeStep) || timeStep <= 0.0D) return current;
        double travel = Math.max(0.0D, policy(axes).filter(velocity).dot(current))
                * timeStep * 0.5D;
        WaypointSpline.RoutePoint anticipated = spline.pointAtDistance(
                projection.distanceAlongRoute() + travel);
        Vec3 dir = policy(axes).filter(anticipated.tangent());
        return dir.lengthSqr() <= 1.0E-12D ? current : dir.normalize();
    }

    // Check position, facing and sideways velocity before attaching
    public static boolean canCapture(Vec3 position, Vec3 velocity, Vec3 forward, Vec3 up,
                                     WaypointSpline.Projection projection, AxisPolicy axes,
                                     double captureRadius, double maximumHeadingAngle,
                                     double maximumLateralSpeed){
        if(!finite(velocity) || !canAttach(position, forward, up, projection, axes,
                captureRadius, maximumHeadingAngle)) return false;
        AxisPolicy policy = policy(axes);
        Vec3 dir = tangent(projection, policy);
        Vec3 motion = policy.filter(velocity);
        Vec3 lateral = motion.subtract(dir.scale(motion.dot(dir)));
        double speed = Math.max(0.0D, finite(maximumLateralSpeed));
        return lateral.lengthSqr() <= speed * speed && motion.dot(dir) >= -0.05D;
    }

    // Check an overlap-certified attachment pose without rejecting recoverable momentum
    public static boolean canAttach(Vec3 position, Vec3 forward, Vec3 up,
                                    WaypointSpline.Projection projection, AxisPolicy axes,
                                    double captureRadius, double maximumHeadingAngle){
        if(!finite(position) || !finite(forward) || !finite(up)
                || projection == null || !projection.found()) return false;
        AxisPolicy policy = policy(axes);
        Vec3 dir = tangent(projection, policy);
        Vec3 facing = policy.filter(forward);
        if(dir.lengthSqr() <= 1.0E-12D || facing.lengthSqr() <= 1.0E-12D) return false;
        double radius = Math.max(0.0D, finite(captureRadius));
        if(policy.filter(position.subtract(projection.position())).lengthSqr()
                > radius * radius) return false;
        double angle = Math.max(0.0D, Math.min(Math.PI, finite(maximumHeadingAngle)));
        if(facing.normalize().dot(dir) < Math.cos(angle)) return false;
        if(policy == AxisPolicy.ALL){
            Vec3 routeUp = dir.cross(new Vec3(0.0D, 1.0D, 0.0D).cross(dir)).normalize();
            if(routeUp.lengthSqr() <= 1.0E-12D) return false;
            if(up.normalize().dot(routeUp) < Math.cos(angle)) return false;
        }
        return true;
    }

    // Check centreline overlap for a compliant capture
    public static boolean canGuide(Vec3 position, Vec3 velocity, Vec3 forward,
                                   WaypointSpline.Projection projection, AxisPolicy axes,
                                   double guideRadius){
        if(!finite(position) || !finite(velocity) || !finite(forward)
                || projection == null || !projection.found()) return false;
        AxisPolicy policy = policy(axes);
        Vec3 dir = tangent(projection, policy);
        if(dir.lengthSqr() <= 1.0E-12D) return false;
        double radius = Math.max(0.0D, finite(guideRadius));
        return policy.filter(position.subtract(projection.position())).lengthSqr()
                <= radius * radius;
    }

    // Find an ordered spline point which overlaps one physical hull
    public static HullCapture captureHull(
            WaypointSpline spline,
            int segmentIndex,
            double minimumFraction,
            List<AABB> worldBounds,
            AxisPolicy axes,
            double padding
    ){
        if(spline == null || spline.isEmpty() || worldBounds == null
                || worldBounds.isEmpty()) return HullCapture.none();
        int first = Math.max(0, Math.min(segmentIndex,
                spline.segments().size() - 1));
        double minimum = clamp(minimumFraction, 0.0D, 1.0D);
        int last = Math.min(first + 1, spline.segments().size() - 1);
        return captureHull(spline, first, minimum, last, worldBounds, axes, padding);
    }

    // Find a physical hull overlap anywhere on a route when its ordered cursor was lost
    public static HullCapture captureHullAnywhere(
            WaypointSpline spline,
            List<AABB> worldBounds,
            AxisPolicy axes,
            double padding
    ){
        if(spline == null || spline.isEmpty() || worldBounds == null
                || worldBounds.isEmpty()) return HullCapture.none();
        return captureHull(spline, 0, 0.0D, spline.segments().size() - 1,
                worldBounds, axes, padding);
    }

    // Find the nearest overlapping route point within one inclusive segment range
    private static HullCapture captureHull(
            WaypointSpline spline,
            int first,
            double minimumFraction,
            int last,
            List<AABB> worldBounds,
            AxisPolicy axes,
            double padding
    ){
        double maximumSeparation = Math.max(0.0D, finite(padding));
        HullCapture best = HullCapture.none();
        for(int idx = first; idx <= last; idx++){
            WaypointSpline.Segment segment = spline.segments().get(idx);
            if(!segmentMayReachHull(segment, worldBounds, policy(axes),
                    maximumSeparation)) continue;
            double segmentMinimum = idx == first ? minimumFraction : 0.0D;
            HullPoint closest = closestHullPoint(
                    segment, segmentMinimum, worldBounds, policy(axes));
            if(closest == null || closest.separation() > maximumSeparation + 1.0E-8D){
                continue;
            }
            WaypointSpline.Projection projection = spline.projectSegment(
                    closest.routePoint(), idx, closest.fraction());
            if(!projection.found()) continue;
            HullCapture candidate = new HullCapture(
                    true, closest.anchor(), projection, closest.separation());
            if(!best.found() || candidate.separation() + 1.0E-8D < best.separation()
                    || Math.abs(candidate.separation() - best.separation()) <= 1.0E-8D
                    && candidate.projection().distanceAlongRoute()
                    > best.projection().distanceAlongRoute()){
                best = candidate;
            }
        }
        return best;
    }

    // Reject curve segments whose cubic Bezier control hull cannot reach the body hull
    private static boolean segmentMayReachHull(
            WaypointSpline.Segment segment,
            List<AABB> worldBounds,
            AxisPolicy axes,
            double padding
    ){
        Vec3 firstControl = segment.start().add(segment.startTangent().scale(1.0D / 3.0D));
        Vec3 secondControl = segment.end().subtract(segment.endTangent().scale(1.0D / 3.0D));
        double minX = Math.min(Math.min(segment.start().x, segment.end().x),
                Math.min(firstControl.x, secondControl.x)) - padding;
        double minY = Math.min(Math.min(segment.start().y, segment.end().y),
                Math.min(firstControl.y, secondControl.y)) - padding;
        double minZ = Math.min(Math.min(segment.start().z, segment.end().z),
                Math.min(firstControl.z, secondControl.z)) - padding;
        double maxX = Math.max(Math.max(segment.start().x, segment.end().x),
                Math.max(firstControl.x, secondControl.x)) + padding;
        double maxY = Math.max(Math.max(segment.start().y, segment.end().y),
                Math.max(firstControl.y, secondControl.y)) + padding;
        double maxZ = Math.max(Math.max(segment.start().z, segment.end().z),
                Math.max(firstControl.z, secondControl.z)) + padding;
        for(AABB bounds : worldBounds){
            if(bounds == null || maxX < bounds.minX || minX > bounds.maxX
                    || maxZ < bounds.minZ || minZ > bounds.maxZ) continue;
            if(axes == AxisPolicy.HORIZONTAL
                    || maxY >= bounds.minY && minY <= bounds.maxY) return true;
        }
        return false;
    }

    // Find the closest point between one ordered curve segment and the hull
    private static HullPoint closestHullPoint(
            WaypointSpline.Segment segment,
            double minimumFraction,
            List<AABB> worldBounds,
            AxisPolicy axes
    ){
        double minimum = clamp(minimumFraction, 0.0D, 1.0D);
        int samples = Math.max(HULL_CAPTURE_MIN_SAMPLES, Math.min(
                HULL_CAPTURE_MAX_SAMPLES,
                (int)Math.ceil(segment.length() / HULL_CAPTURE_SAMPLE_SPACING)));
        HullPoint best = hullPoint(segment, minimum, worldBounds, axes);
        for(int idx = 1; idx <= samples; idx++){
            double fraction = minimum + (1.0D - minimum) * idx / samples;
            HullPoint candidate = hullPoint(segment, fraction, worldBounds, axes);
            if(candidate != null && (best == null
                    || candidate.separation() + 1.0E-8D < best.separation()
                    || Math.abs(candidate.separation() - best.separation()) <= 1.0E-8D
                    && candidate.fraction() > best.fraction())) best = candidate;
        }
        if(best == null) return null;
        double radius = (1.0D - minimum) / samples;
        double low = Math.max(minimum, best.fraction() - radius);
        double high = Math.min(1.0D, best.fraction() + radius);
        for(int idx = 0; idx < HULL_CAPTURE_REFINEMENT_STEPS; idx++){
            double left = low + (high - low) / 3.0D;
            double right = high - (high - low) / 3.0D;
            HullPoint leftPoint = hullPoint(segment, left, worldBounds, axes);
            HullPoint rightPoint = hullPoint(segment, right, worldBounds, axes);
            if(leftPoint != null && (rightPoint == null
                    || leftPoint.separation() <= rightPoint.separation())){
                high = right;
                if(leftPoint.separation() < best.separation()) best = leftPoint;
            }else{
                low = left;
                if(rightPoint != null && rightPoint.separation() < best.separation()){
                    best = rightPoint;
                }
            }
        }
        return best;
    }

    // Resolve the nearest material anchor for one curve sample
    private static HullPoint hullPoint(
            WaypointSpline.Segment segment,
            double fraction,
            List<AABB> worldBounds,
            AxisPolicy axes
    ){
        Vec3 routePoint = segment.pointAtFraction(fraction);
        HullPoint best = null;
        for(AABB bounds : worldBounds){
            if(bounds == null) continue;
            double x = clamp(routePoint.x, bounds.minX, bounds.maxX);
            double y = axes == AxisPolicy.HORIZONTAL
                    ? (bounds.minY + bounds.maxY) * 0.5D
                    : clamp(routePoint.y, bounds.minY, bounds.maxY);
            double z = clamp(routePoint.z, bounds.minZ, bounds.maxZ);
            Vec3 anchor = new Vec3(x, y, z);
            Vec3 separation = axes.filter(routePoint.subtract(anchor));
            HullPoint candidate = new HullPoint(
                    fraction, routePoint, anchor, separation.length());
            if(best == null || candidate.separation() < best.separation()){
                best = candidate;
            }
        }
        return best;
    }

    // Build a right-handed frame whose local Z points forward
    public static Quaterniond orientation(Vec3 forward, Vec3 up){
        if(!finite(forward) || !finite(up) || forward.lengthSqr() <= 1.0E-12D){
            throw new IllegalArgumentException("Finite forward and up directions are required");
        }
        Vec3 dir = forward.normalize();
        Vec3 right = up.cross(dir);
        if(right.lengthSqr() <= 1.0E-12D){
            Vec3 fallback = Math.abs(dir.y) < 0.9D
                    ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
            right = fallback.cross(dir);
        }
        right = right.normalize();
        Vec3 correctedUp = dir.cross(right).normalize();
        Matrix3d basis = new Matrix3d(
                right.x, right.y, right.z,
                correctedUp.x, correctedUp.y, correctedUp.z,
                dir.x, dir.y, dir.z);
        return new Quaterniond().setFromNormalized(basis).normalize();
    }

    // Carry longitudinal momentum into a new tangent without adding speed
    public static Vec3 transportVelocity(Vec3 velocity, Vec3 prevDirection,
                                         Vec3 nextDirection, AxisPolicy axes){
        if(!finite(velocity) || !finite(prevDirection) || !finite(nextDirection)) return Vec3.ZERO;
        AxisPolicy policy = policy(axes);
        Vec3 prev = policy.filter(prevDirection).normalize();
        Vec3 next = policy.filter(nextDirection).normalize();
        if(prev.lengthSqr() <= 1.0E-12D || next.lengthSqr() <= 1.0E-12D) return velocity;
        Vec3 motion = policy.filter(velocity);
        double speed = motion.dot(prev);
        Vec3 lateral = motion.subtract(prev.scale(speed));
        Quaterniond turn = new Quaterniond().rotationTo(prev.x, prev.y, prev.z, next.x, next.y, next.z);
        org.joml.Vector3d rotated = turn.transform(new org.joml.Vector3d(lateral.x, lateral.y, lateral.z));
        return next.scale(speed).add(new Vec3(rotated.x, rotated.y, rotated.z))
                .add(velocity.subtract(motion));
    }

    // Change only longitudinal speed, preserving lateral solver work and free vertical motion
    public static Vec3 driveVelocity(Vec3 velocity, Vec3 direction, AxisPolicy axes,
                                     double targetSpeed, double acceleration,
                                     double brakingAcceleration, double timeStep){
        if(!finite(velocity) || !finite(direction) || !Double.isFinite(timeStep) || timeStep <= 0.0D){
            return velocity;
        }
        Vec3 dir = policy(axes).filter(direction).normalize();
        if(dir.lengthSqr() <= 1.0E-12D) return velocity;
        double current = velocity.dot(dir);
        double error = Math.max(0.0D, finite(targetSpeed)) - current;
        double rate = Math.max(0.0D, finite(error >= 0.0D ? acceleration : brakingAcceleration));
        double delta = Math.max(-rate * timeStep, Math.min(rate * timeStep, error));
        return velocity.add(dir.scale(delta));
    }

    // Remove cross-track motion without adding any position-correction velocity
    public static Vec3 retainFrameVelocity(
            Vec3 velocity,
            Vec3 position,
            WaypointSpline.Projection projection,
            AxisPolicy axes,
            double timeStep,
            double maximumCorrectionSpeed
    ){
        if(!finite(velocity) || !finite(position) || projection == null || !projection.found()
                || !Double.isFinite(timeStep) || timeStep <= 0.0D) return velocity;
        AxisPolicy policy = policy(axes);
        Vec3 dir = tangent(projection, policy);
        if(dir.lengthSqr() <= 1.0E-12D) return velocity;
        Vec3 motion = policy.filter(velocity);
        Vec3 free = velocity.subtract(motion);
        Vec3 longitudinal = dir.scale(motion.dot(dir));
        Vec3 lateral = motion.subtract(longitudinal);
        double maximum = Math.max(0.0D, finite(maximumCorrectionSpeed)) * timeStep;
        if(lateral.lengthSqr() <= maximum * maximum) lateral = Vec3.ZERO;
        else if(maximum > 0.0D){
            lateral = lateral.subtract(lateral.normalize().scale(maximum));
        }
        return free.add(longitudinal).add(lateral);
    }

    // Check that the route has not reached its normal-control handoff
    public static boolean beforeHandoff(WaypointSpline spline,
                                        WaypointSpline.Projection projection,
                                        double handoffDistance){
        return spline != null && !spline.isEmpty() && projection != null && projection.found()
                && spline.length() - projection.distanceAlongRoute()
                > Math.max(0.0D, finite(handoffDistance));
    }

    // Check the next physics step before moving the retained world frame
    public static boolean beforePredictedHandoff(
            WaypointSpline spline,
            WaypointSpline.Projection projection,
            Vec3 velocity,
            AxisPolicy axes,
            double timeStep,
            double handoffDistance
    ){
        if(!beforeHandoff(spline, projection, handoffDistance)
                || !finite(velocity) || !Double.isFinite(timeStep) || timeStep <= 0.0D) return false;
        Vec3 dir = tangent(projection, axes);
        if(dir.lengthSqr() <= 1.0E-12D) return false;
        double remaining = spline.length() - projection.distanceAlongRoute()
                - Math.max(0.0D, finite(handoffDistance));
        double forwardTravel = Math.max(0.0D, policy(axes).filter(velocity).dot(dir)) * timeStep;
        return forwardTravel + 1.0E-6D < remaining;
    }

    // Resolve the default axis policy
    private static AxisPolicy policy(AxisPolicy axes){
        return axes == null ? AxisPolicy.ALL : axes;
    }

    // Check one finite vector
    private static boolean finite(Vec3 val){
        return val != null && Double.isFinite(val.x)
                && Double.isFinite(val.y) && Double.isFinite(val.z);
    }

    // Resolve one finite scalar
    private static double finite(double val){
        return Double.isFinite(val) ? val : 0.0D;
    }

    // Clamp one finite scalar
    private static double clamp(double val, double min, double max){
        return Math.max(min, Math.min(max, finite(val)));
    }

    // Store one hull-backed route attachment point
    public record HullCapture(
            boolean found,
            Vec3 anchor,
            WaypointSpline.Projection projection,
            double separation
    ){
        // Create one missing hull capture
        public static HullCapture none(){
            return new HullCapture(false, Vec3.ZERO,
                    WaypointSpline.Projection.notFound(), Double.POSITIVE_INFINITY);
        }
    }

    // Store one internal hull-distance sample
    private record HullPoint(
            double fraction,
            Vec3 routePoint,
            Vec3 anchor,
            double separation
    ){}

    // Keep vertical travel and terrain tilt free for ground and sea craft
    public enum AxisPolicy{
        ALL,
        HORIZONTAL;

        // Filter a vector to this policy
        public Vec3 filter(Vec3 val){
            if(!finite(val)) return Vec3.ZERO;
            return this == HORIZONTAL ? new Vec3(val.x, 0.0D, val.z) : val;
        }
    }
}
