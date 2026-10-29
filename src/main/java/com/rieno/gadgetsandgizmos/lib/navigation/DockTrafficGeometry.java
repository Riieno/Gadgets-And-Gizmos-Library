package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelParticleOcclusion;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

// Measure lane access against loaded hulls without depending on a ship or dock implementation
public final class DockTrafficGeometry{
    public static final double BLOCKED_COST = 1.0E9D;
    private DockTrafficGeometry(){}

    public record Obstacle(UUID owner, List<AABB> bounds){
        public Obstacle{ bounds = List.copyOf(bounds); }
    }

    public record Movement(UUID owner, List<Vec3> controls, List<AABB> hull, double speed){
        public Movement(UUID owner, List<Vec3> controls, List<AABB> hull){ this(owner, controls, hull, 1.0D); }
        public Movement{
            controls = List.copyOf(controls); hull = List.copyOf(hull);
            speed = Double.isFinite(speed) ? Math.max(0, speed) : 0;
        }
    }

    // Forecast berth occupation separately from the hulls which currently prevent travel
    public record Traffic(List<Obstacle> berths, List<Movement> movements){
        public static final Traffic EMPTY = new Traffic(List.of(), List.of());
        public Traffic{ berths = List.copyOf(berths); movements = List.copyOf(movements); }
    }

    public static double trafficCost(List<Vec3> controls, List<AABB> hull, Traffic traffic){
        double cost = 0;
        for(var berth : traffic.berths()) if(blocker(controls, hull, List.of(berth)) != null) cost += 1.0E6D;
        for(var movement : traffic.movements()) if(routesConflict(controls, hull, movement.controls(), movement.hull())) cost += 1.0E6D;
        return cost;
    }

    // Prefer an unreserved corridor before considering lane order or external travel distance
    public static java.util.Optional<SplinePathNetwork.Route> selectRoute(List<SplinePathNetwork.Route> routes,
            Vec3 position, List<AABB> hull, Traffic traffic){
        return routes.stream().min(java.util.Comparator.comparingDouble((SplinePathNetwork.Route route) -> trafficCost(route.controls(), hull, traffic))
                .thenComparingInt(SplinePathNetwork.Route::priority)
                .thenComparingDouble(route -> route.length() + (position == null ? 0 : position.distanceTo(route.endpoint())))
                .thenComparing(SplinePathNetwork.Route::endpointId));
    }

    public record Assessment(List<Vec3> controls, double exitDistance, UUID blocker){
        public Assessment{ controls = List.copyOf(controls); }
        public boolean reachable(){ return !controls.isEmpty() && exitDistance < Double.MAX_VALUE; }
    }

    // Let established lane traffic pass before reserving a crossing docking leg
    public static UUID dockingBlocker(List<Vec3> leg, List<AABB> hull, Collection<Obstacle> obstacles, Traffic traffic, double seconds){
        UUID blocked = blocker(leg, hull, obstacles);
        if(blocked != null) return blocked;
        double horizon = Double.isFinite(seconds) ? Math.max(0, seconds) : 0;
        for(var movement : traffic.movements()){
            if(routesConflict(leg, hull, routePrefix(movement.controls(), movement.speed() * horizon), movement.hull())) return movement.owner();
        }
        return null;
    }

    // Protect every hull segment until the complete docking translation has finished
    public static List<AABB> sweptBounds(List<Vec3> route, List<AABB> hull, double margin){
        List<AABB> res = new ArrayList<>();
        for(int idx = 0; idx < route.size(); idx++) for(AABB box : hull){
            res.add(box.move(route.get(idx)).minmax(box.move(route.get(Math.min(idx + 1, route.size() - 1)))).inflate(Math.max(0, margin)));
        }
        return List.copyOf(res);
    }

    private static List<Vec3> routePrefix(List<Vec3> route, double distance){
        if(route.isEmpty()) return route;
        List<Vec3> res = new ArrayList<>();
        res.add(route.getFirst());
        for(int idx = 1; idx < route.size() && distance > 0; idx++){
            Vec3 prev = route.get(idx - 1), next = route.get(idx);
            double length = prev.distanceTo(next);
            if(length > distance){ res.add(prev.lerp(next, distance / length)); break; }
            res.add(next);
            distance -= length;
        }
        return List.copyOf(res);
    }

    // Refresh blocked travel without sending a captured ship back to an entrance or across an unconnected lane
    public static SplineRouteTraversal refreshApproach(DockingApproachPath approach, Vec3 tip, Vec3 facing, Vec3 up,
            SplineRouteTraversal retained, Vec3 position, List<AABB> hull, Collection<Obstacle> obstacles){
        return refreshApproach(approach, tip, facing, up, retained, position, hull, obstacles, Traffic.EMPTY);
    }

    public static SplineRouteTraversal refreshApproach(DockingApproachPath approach, Vec3 tip, Vec3 facing, Vec3 up,
            SplineRouteTraversal retained, Vec3 position, List<AABB> hull, Collection<Obstacle> obstacles, Traffic traffic){
        List<Vec3> remaining = retained.captured() ? retained.remainingControls(position) : retained.spline().waypoints();
        if(blocker(remaining, hull, obstacles) == null && blocker(List.of(retained.spline().waypoints().getLast(), tip), hull, obstacles) == null
                && trafficCost(remaining, hull, traffic) == 0) return retained;
        SplinePathNetwork network = approach.worldNetwork(tip, facing, up);
        List<SplinePathNetwork.Route> available = new ArrayList<>();
        java.util.Map<SplinePathNetwork.Route, SplineRouteTraversal> continuations = new java.util.HashMap<>();
        for(var candidate : approach.routes(tip, facing, up, false,
                controls -> occupancyCost(controls, hull, obstacles) + trafficCost(controls, hull, traffic) + detourCost(network, controls))){
            var next = retained.withContinuation(WaypointSpline.of(candidate.controls()), 0.25D);
            if(next.isEmpty()) continue;
            List<Vec3> controls = next.get().captured() ? next.get().remainingControls(position) : candidate.controls();
            if(blocker(controls, hull, obstacles) == null && blocker(List.of(candidate.controls().getLast(), tip), hull, obstacles) == null){
                var route = new SplinePathNetwork.Route(candidate.endpointId(), candidate.endpoint(), controls,
                        WaypointSpline.of(controls).length(), candidate.priority());
                available.add(route);
                continuations.put(route, next.get());
            }
        }
        var selected = selectRoute(available, position, hull, traffic);
        if(selected.isEmpty() || trafficCost(selected.get().controls(), hull, traffic) >= trafficCost(remaining, hull, traffic)
                && blocker(remaining, hull, obstacles) == null && blocker(List.of(retained.spline().waypoints().getLast(), tip), hull, obstacles) == null) return retained;
        return continuations.get(selected.get());
    }

    // Evaluate both directions before provisioning a berth, preferring the primary lane when clear
    public static Assessment assess(DockingApproachPath approach, Vec3 tip, Vec3 facing, Vec3 up, Vec3 position,
            List<AABB> hull, Collection<Obstacle> obstacles){
        return assess(approach, tip, facing, up, position, hull, obstacles, null);
    }

    // A ship already on a lane may continue forward, but cannot recapture another lane's entry or docking branch
    public static Assessment assess(DockingApproachPath approach, Vec3 tip, Vec3 facing, Vec3 up, Vec3 position,
            List<AABB> hull, Collection<Obstacle> obstacles, Vec3 continuingForward){
        return assess(approach, tip, facing, up, position, hull, obstacles, continuingForward, false);
    }

    // A temporary traffic obstruction may require waiting on a valid directed lane instead of abandoning it
    public static Assessment assess(DockingApproachPath approach, Vec3 tip, Vec3 facing, Vec3 up, Vec3 position,
            List<AABB> hull, Collection<Obstacle> obstacles, Vec3 continuingForward, boolean waitForTraffic){
        return assess(approach, tip, facing, up, position, hull, obstacles, continuingForward, waitForTraffic, Traffic.EMPTY);
    }

    public static Assessment assess(DockingApproachPath approach, Vec3 tip, Vec3 facing, Vec3 up, Vec3 position,
            List<AABB> hull, Collection<Obstacle> obstacles, Vec3 continuingForward, boolean waitForTraffic, Traffic traffic){
        SplinePathNetwork network = approach.worldNetwork(tip, facing, up);
        var candidates = approach.routes(tip, facing, up, false,
                controls -> occupancyCost(controls, hull, obstacles) + trafficCost(controls, hull, traffic) + detourCost(network, controls));
        List<SplinePathNetwork.Route> available = new ArrayList<>();
        List<SplinePathNetwork.Route> waiting = new ArrayList<>();
        UUID blocker = null;
        for(var candidate : candidates){
            if(continuingForward != null){
                var projection = WaypointSpline.of(candidate.controls()).project(position);
                if(!projection.found() || projection.distance() > 2.0D
                        || projection.tangent().normalize().dot(continuingForward.normalize()) < 0.5D) continue;
            }
            List<Vec3> remaining = continuingForward == null ? candidate.controls() : remainingRoute(candidate.controls(), position, 2.0D);
            UUID blocked = blocker(remaining, hull, obstacles);
            if(blocked == null) blocked = blocker(List.of(candidate.controls().getLast(), tip), hull, obstacles);
            var route = new SplinePathNetwork.Route(candidate.endpointId(), remaining.getFirst(), remaining, candidate.length(), candidate.priority());
            waiting.add(route);
            if(blocked == null) available.add(route);
            else blocker = blocked;
        }
        var selected = selectRoute(available, position, hull, traffic);
        if(selected.isEmpty() && waitForTraffic) selected = selectRoute(waiting, position, hull, traffic);
        var exits = approach.routes(tip, facing, up, true,
                controls -> occupancyCost(controls, hull, obstacles) + trafficCost(controls, hull, traffic) + detourCost(network, controls));
        // A temporarily occupied exit must not prevent arrival at an otherwise reachable free berth
        for(var candidate : exits){
            UUID blocked = blocker(candidate.controls(), hull, obstacles);
            if(blocker == null && blocked != null) blocker = blocked;
        }
        var exit = selectRoute(exits, null, hull, traffic);
        return new Assessment(selected.map(SplinePathNetwork.Route::controls).orElse(List.of()),
                exit.map(SplinePathNetwork.Route::length).orElse(Double.MAX_VALUE), blocker);
    }

    // Expand blockers by the moving hull and sweep each complete leg, including between samples
    public static UUID blocker(List<Vec3> route, List<AABB> relativeHull, Collection<Obstacle> obstacles){
        if(route.isEmpty() || relativeHull.isEmpty()) return null;
        for(Obstacle obstacle : obstacles) for(AABB fixed : obstacle.bounds()) for(AABB hull : relativeHull){
            AABB expanded = new AABB(fixed.minX - hull.maxX, fixed.minY - hull.maxY, fixed.minZ - hull.maxZ,
                    fixed.maxX - hull.minX, fixed.maxY - hull.minY, fixed.maxZ - hull.minZ).inflate(0.25D);
            Vec3 prev = route.getFirst();
            if(expanded.contains(prev)) return obstacle.owner();
            for(int idx = 1; idx < route.size(); idx++){
                Vec3 next = route.get(idx);
                if(expanded.contains(next) || expanded.clip(prev, next).isPresent()) return obstacle.owner();
                prev = next;
            }
        }
        return null;
    }

    // A small ship may use a narrow bypass that is correctly unavailable to a large hull
    public static double occupancyCost(List<Vec3> route, List<AABB> hull, Collection<Obstacle> obstacles){
        return blocker(route, hull, obstacles) == null ? 0 : BLOCKED_COST;
    }

    // Parallel lanes conflict only when the actual swept hulls share space
    public static boolean routesConflict(List<Vec3> first, List<AABB> firstHull, List<Vec3> second, List<AABB> secondHull){
        if(first.isEmpty() || second.isEmpty()) return false;
        for(AABB a : firstHull) for(AABB b : secondHull){
            AABB expanded = new AABB(b.minX - a.maxX, b.minY - a.maxY, b.minZ - a.maxZ,
                    b.maxX - a.minX, b.maxY - a.minY, b.maxZ - a.minZ).inflate(0.25D);
            for(int idx = 0; idx < Math.max(1, first.size() - 1); idx++){
                Vec3 start = first.get(idx), end = first.get(Math.min(idx + 1, first.size() - 1));
                for(int other = 0; other < Math.max(1, second.size() - 1); other++){
                    if(segmentsConflict(start, end, second.get(other), second.get(Math.min(other + 1, second.size() - 1)), expanded)) return true;
                }
            }
        }
        return false;
    }

    // Clip both segment fractions against the three hull overlap intervals
    private static boolean segmentsConflict(Vec3 start, Vec3 end, Vec3 otherStart, Vec3 otherEnd, AABB bounds){
        if(Math.min(start.x, end.x) - Math.max(otherStart.x, otherEnd.x) > bounds.maxX
                || Math.max(start.x, end.x) - Math.min(otherStart.x, otherEnd.x) < bounds.minX
                || Math.min(start.y, end.y) - Math.max(otherStart.y, otherEnd.y) > bounds.maxY
                || Math.max(start.y, end.y) - Math.min(otherStart.y, otherEnd.y) < bounds.minY
                || Math.min(start.z, end.z) - Math.max(otherStart.z, otherEnd.z) > bounds.maxZ
                || Math.max(start.z, end.z) - Math.min(otherStart.z, otherEnd.z) < bounds.minZ) return false;
        Vec3 origin = start.subtract(otherStart), first = end.subtract(start), second = otherEnd.subtract(otherStart);
        List<RouteFraction> polygon = List.of(new RouteFraction(0, 0), new RouteFraction(1, 0), new RouteFraction(1, 1), new RouteFraction(0, 1));
        double[] base = {origin.x, origin.y, origin.z}, a = {first.x, first.y, first.z}, b = {second.x, second.y, second.z};
        double[] min = {bounds.minX, bounds.minY, bounds.minZ}, max = {bounds.maxX, bounds.maxY, bounds.maxZ};
        for(int axis = 0; axis < 3 && !polygon.isEmpty(); axis++){
            polygon = clipFractions(polygon, a[axis], -b[axis], max[axis] - base[axis]);
            polygon = clipFractions(polygon, -a[axis], b[axis], base[axis] - min[axis]);
        }
        return !polygon.isEmpty();
    }

    private static List<RouteFraction> clipFractions(List<RouteFraction> polygon, double first, double second, double boundary){
        if(polygon.isEmpty()) return polygon;
        List<RouteFraction> res = new ArrayList<>();
        RouteFraction prev = polygon.getLast();
        double previous = first * prev.first() + second * prev.second() - boundary;
        for(RouteFraction point : polygon){
            double val = first * point.first() + second * point.second() - boundary;
            if((val <= 1.0E-9D) != (previous <= 1.0E-9D)){
                double fraction = previous / (previous - val);
                res.add(new RouteFraction(prev.first() + (point.first() - prev.first()) * fraction,
                        prev.second() + (point.second() - prev.second()) * fraction));
            }
            if(val <= 1.0E-9D) res.add(point);
            prev = point;
            previous = val;
        }
        return res;
    }

    private record RouteFraction(double first, double second){}

    // Measure braking room along the ordered lane using the vessel's actual hull
    public static double clearance(List<Vec3> route, double range, List<AABB> relativeHull, Collection<Obstacle> obstacles){
        double res = Double.isFinite(range) ? Math.max(0, range) : 0;
        double distance = 0;
        for(int idx = 0; idx < route.size() && distance < res; idx++){
            Vec3 start = route.get(idx), end = route.get(Math.min(idx + 1, route.size() - 1));
            double length = start.distanceTo(end);
            if(length <= 1.0E-9D && route.size() > 1) continue;
            if(length > res - distance) end = start.lerp(end, (res - distance) / length);
            for(Obstacle obstacle : obstacles) for(AABB fixed : obstacle.bounds()) for(AABB hull : relativeHull){
                AABB expanded = new AABB(fixed.minX - hull.maxX, fixed.minY - hull.maxY, fixed.minZ - hull.maxZ,
                        fixed.maxX - hull.minX, fixed.maxY - hull.minY, fixed.maxZ - hull.minZ).inflate(0.25D);
                if(expanded.contains(start)){
                    // A follower behind the hull cannot veto translation away from it
                    Vec3 delta = end.subtract(start);
                    if(SubLevelParticleOcclusion.allowsOverlapDeparture(List.of(hull.move(start).inflate(0.25D)),
                            fixed, delta.normalize(), delta.length())) continue;
                    return distance;
                }
                var hit = expanded.clip(start, end);
                if(hit.isPresent()) res = Math.min(res, distance + start.distanceTo(hit.get()));
            }
            distance += length;
        }
        return res;
    }

    // Measure stopping room before another vessel's reserved docking sweep
    public static double clearance(Vec3 position, Vec3 direction, double range, List<AABB> relativeHull, Collection<Obstacle> obstacles){
        Vec3 end = position.add(direction.normalize().scale(range));
        double res = range;
        for(Obstacle obstacle : obstacles) for(AABB fixed : obstacle.bounds()) for(AABB hull : relativeHull){
            AABB expanded = new AABB(fixed.minX - hull.maxX, fixed.minY - hull.maxY, fixed.minZ - hull.maxZ,
                    fixed.maxX - hull.minX, fixed.maxY - hull.minY, fixed.maxZ - hull.minZ);
            // A ship already inside must be allowed to leave instead of becoming a permanent blocker
            if(expanded.contains(position)) continue;
            var hit = expanded.clip(position, end);
            if(hit.isPresent()) res = Math.min(res, position.distanceTo(hit.get()));
        }
        return res;
    }

    // Remove controls already passed without acquiring a later crossing from a distant lane
    public static List<Vec3> remainingRoute(List<Vec3> controls, Vec3 position, double captureRadius){
        WaypointSpline spline = WaypointSpline.of(controls);
        var projection = spline.project(position);
        if(!projection.found() || projection.distance() > captureRadius) return controls;
        List<Vec3> res = new ArrayList<>();
        res.add(projection.position());
        res.addAll(controls.subList(Math.min(controls.size(), projection.segmentIndex() + 1), controls.size()));
        return WaypointSpline.of(res).waypoints();
    }

    // Additional lanes are detours; distance alone must never make an empty dock use them
    public static double detourCost(SplinePathNetwork network, List<Vec3> controls){
        if(network.isEmpty() || controls.isEmpty()) return 0;
        return network.followsPrimaryLane(controls) ? 0 : 10_000.0D;
    }
}
