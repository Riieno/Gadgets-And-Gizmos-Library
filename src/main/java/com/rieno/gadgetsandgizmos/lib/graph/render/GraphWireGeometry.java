package com.rieno.gadgetsandgizmos.lib.graph.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.TreeSet;

// Route graph wires and share their exact paths with drawing, picking and execution pulses
public final class GraphWireGeometry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph wire geometry
    private GraphWireGeometry() {
    }

    public enum Mode{
        STRAIGHT, @Deprecated SMOOTH, BEZIER, ROUNDED;

        public String id(){ return this == SMOOTH ? "bezier" : name().toLowerCase(Locale.ROOT); }
        public static Mode fromId(String id){
            if("rounded".equals(id)) return ROUNDED;
            return "bezier".equals(id) || "smooth".equals(id) ? BEZIER : STRAIGHT;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the segments
    public static List<Segment> segments(double x1, double y1, double x2, double y2) {
        double bendX = (x1 + x2) * 0.5D;
        return List.of(
                new Segment(new Point(x1, y1), new Point(bendX, y1)),
                new Segment(new Point(bendX, y1), new Point(bendX, y2)),
                new Segment(new Point(bendX, y2), new Point(x2, y2)));
    }

    // Get the point
    public static Point pointAt(double x1, double y1, double x2, double y2, double amount) {
        List<Segment> parts = segments(x1, y1, x2, y2);
        double totalLength = parts.stream().mapToDouble(Segment::length).sum();
        if (totalLength <= 0.0D) return new Point(x1, y1);
        double remaining = Math.max(0.0D, Math.min(1.0D, amount)) * totalLength;
        for (Segment segment : parts) {
            double length = segment.length();
            if (remaining <= length || segment == parts.getLast()) {
                double local = length <= 0.0D ? 0.0D : Math.min(1.0D, remaining / length);
                return new Point(
                        segment.start().x() + (segment.end().x() - segment.start().x()) * local,
                        segment.start().y() + (segment.end().y() - segment.start().y()) * local);
            }
            remaining -= length;
        }
        return new Point(x2, y2);
    }

    // Get the distance squared to wire
    public static double distanceSquaredToWire(double x, double y,
                                               double x1, double y1, double x2, double y2) {
        double closest = Double.MAX_VALUE;
        for (Segment segment : segments(x1, y1, x2, y2)) {
            closest = Math.min(closest, distanceSquared(x, y, segment.start(), segment.end()));
        }
        return closest;
    }

    // Retain socket-owner turns while allowing hosts to disable detours around unrelated nodes
    public static Path route(Endpoint source, Endpoint target, List<Bounds> obstacles, Mode mode, double clearance, boolean avoidNodes){
        if(avoidNodes) return route(source, target, obstacles, mode, clearance);
        List<Bounds> owners = new ArrayList<>();
        if(source.owner() != null) owners.add(source.owner());
        if(target.owner() != null && !owners.contains(target.owner())) owners.add(target.owner());
        return route(source, target, owners, mode, clearance);
    }

    // Leave both sockets along their outward directions and keep the middle route outside node bodies
    public static Path route(Endpoint source, Endpoint target, List<Bounds> obstacles, Mode mode, double clearance){
        double gap = Math.max(4, clearance);
        boolean straight = mode == Mode.STRAIGHT || mode == Mode.ROUNDED;
        Point start = lead(source, gap * 3, obstacles), end = lead(target, gap * 3, obstacles);
        if(straight){
            List<Point> leads = straightLeads(source, target, start, end);
            start = leads.get(0); end = leads.get(1);
        }
        List<Bounds> blocked = obstacles.stream().map(rect -> rect.inflated(gap)).toList();
        Point direct = unit(target.position().x() - source.position().x(), target.position().y() - source.position().y());
        if(direct.x() * source.outward().x() + direct.y() * source.outward().y() > 1 - 1.0E-8D
                && direct.x() * target.outward().x() + direct.y() * target.outward().y() < -1 + 1.0E-8D){
            List<Point> line = List.of(source.position(), target.position());
            if(clearInterior(line, blocked, source.owner(), target.owner(), gap)){
                if(straight) return new Path(line);
                double handle = distance(source.position(), target.position()) / 3;
                return Path.fromBeziers(List.of(new Bezier(source.position(), add(source.position(), direct, handle),
                        add(target.position(), direct, -handle), target.position())));
            }
        }
        if(!straight){
            Bezier curve = cubic(source.position(), source.outward(), target.position(), target.outward());
            if(clearInterior(curve.sample(), blocked, source.owner(), target.owner(), gap)) return Path.fromBeziers(List.of(curve));
        }
        List<Bounds> routing = new ArrayList<>(), guards = new ArrayList<>(), curveGuards = new ArrayList<>();
        for(Bounds rect : obstacles){
            if(rect.inflated(-1.0E-6D).contains(start) || rect.inflated(-1.0E-6D).contains(end)) continue;
            double padding = gap * 2, available = Math.min(outsideDistance(rect, start), outsideDistance(rect, end));
            if(available < padding) padding = available * 0.5;
            routing.add(rect.inflated(padding)); guards.add(rect.inflated(padding * 0.5));
            curveGuards.add(rect.equals(source.owner()) || rect.equals(target.owner()) ? rect.inflated(-0.01) : rect.inflated(padding * 0.5));
        }
        List<Point> middle = middleRoute(start, end, routing, gap);
        List<Point> points = new ArrayList<>();
        points.add(source.position()); points.addAll(middle); points.add(target.position());
        points = simplify(points);
        if(!straight) return bezierRoute(points, source, target, curveGuards);
        points = bevelCorners(straighten(points, guards, gap), guards, gap * 4);
        return mode == Mode.ROUNDED ? roundedRoute(points, guards, gap * 2) : new Path(points);
    }

    // Face along the connected flow and snap reroutes to one of eight graph directions
    public static Point rerouteDirection(Point center, List<Point> upstream, List<Point> downstream){
        double x = 0, y = 0;
        for(Point point : upstream){ Point dir = unit(center.x() - point.x(), center.y() - point.y()); x += dir.x(); y += dir.y(); }
        for(Point point : downstream){ Point dir = unit(point.x() - center.x(), point.y() - center.y()); x += dir.x(); y += dir.y(); }
        if(Math.hypot(x, y) < 1.0E-8D){
            Point point = downstream.stream().filter(val -> distance(val, center) > 1.0E-8D).findFirst().orElse(null);
            if(point != null){ x = point.x() - center.x(); y = point.y() - center.y(); }
            else if(!upstream.isEmpty()){ x = center.x() - upstream.getFirst().x(); y = center.y() - upstream.getFirst().y(); }
            else return new Point(1, 0);
        }
        double angle = Math.round(Math.atan2(y, x) / (Math.PI / 4)) * (Math.PI / 4);
        return new Point(clean(Math.cos(angle)), clean(Math.sin(angle)));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Point lead(Endpoint endpoint, double length, List<Bounds> obstacles){
        Point pos = endpoint.position(), dir = endpoint.outward();
        Bounds owner = endpoint.owner();
        double exit = 0;
        if(owner != null && owner.contains(pos)){
            double x = Math.abs(dir.x()) < 1.0E-8D ? Double.POSITIVE_INFINITY
                    : ((dir.x() > 0 ? owner.right() : owner.left()) - pos.x()) / dir.x();
            double y = Math.abs(dir.y()) < 1.0E-8D ? Double.POSITIVE_INFINITY
                    : ((dir.y() > 0 ? owner.bottom() : owner.top()) - pos.y()) / dir.y();
            exit = Math.max(0, Math.min(x, y));
        }
        double reach = exit + length;
        for(Bounds rect : obstacles){
            if(rect.equals(owner) || rect.inflated(-1.0E-6D).contains(pos)) continue;
            double entry = rayEntry(pos, dir, rect);
            if(Double.isFinite(entry)) reach = Math.min(reach, entry * 0.5);
        }
        return add(pos, dir, reach);
    }

    // Shorten a socket lead before another node blocks its outward ray
    private static double rayEntry(Point point, Point dir, Bounds rect){
        double low = 0, high = Double.POSITIVE_INFINITY;
        double[] starts = {point.x(), point.y()}, steps = {dir.x(), dir.y()};
        double[] mins = {rect.left(), rect.top()}, maxs = {rect.right(), rect.bottom()};
        for(int axis = 0; axis < 2; axis++){
            if(Math.abs(steps[axis]) < 1.0E-10D){ if(starts[axis] <= mins[axis] || starts[axis] >= maxs[axis]) return Double.POSITIVE_INFINITY; }
            else{
                double a = (mins[axis] - starts[axis]) / steps[axis], b = (maxs[axis] - starts[axis]) / steps[axis];
                low = Math.max(low, Math.min(a, b)); high = Math.min(high, Math.max(a, b));
                if(low > high) return Double.POSITIVE_INFINITY;
            }
        }
        return high > 0 ? low : Double.POSITIVE_INFINITY;
    }

    private static double outsideDistance(Bounds rect, Point point){
        return Math.max(Math.max(rect.left() - point.x(), point.x() - rect.right()),
                Math.max(rect.top() - point.y(), point.y() - rect.bottom()));
    }

    // Reserve enough space for a complete diagonal instead of making socket leads overlap or overshoot
    private static List<Point> straightLeads(Endpoint source, Endpoint target, Point start, Point end){
        Point from = source.outward(), to = target.outward();
        double a = distance(source.position(), start), b = distance(target.position(), end);
        double dx = target.position().x() - source.position().x(), dy = target.position().y() - source.position().y();
        if(from.x() * to.x() + from.y() * to.y() < -1 + 1.0E-8D && (Math.abs(from.x()) < 1.0E-8D || Math.abs(from.y()) < 1.0E-8D)){
            double along = dx * from.x() + dy * from.y(), across = Math.abs(dx * from.y() - dy * from.x());
            if(along > 0){
                double budget = along >= across ? along - across : along * 0.5;
                if(a + b > budget){ double scale = budget / (a + b); a *= scale; b *= scale; }
            }
        }
        double[] delta = {dx, dy}, out = {from.x(), from.y()}, in = {-to.x(), -to.y()};
        for(int axis = 0; axis < 2; axis++){
            if(Math.abs(delta[axis]) < 1.0E-8D) continue;
            double sign = Math.signum(delta[axis]), first = Math.max(0, out[axis] * sign), last = Math.max(0, in[axis] * sign);
            double demand = a * first + b * last;
            if(demand <= Math.abs(delta[axis])) continue;
            double scale = Math.abs(delta[axis]) / demand;
            if(first > 0) a *= scale;
            if(last > 0) b *= scale;
        }
        return List.of(add(source.position(), from, a), add(target.position(), to, b));
    }

    // Replace visible staircases with fewer axis runs and complete 45-degree diagonals
    private static List<Point> straighten(List<Point> points, List<Bounds> blocked, double gap){
        List<Point> res = points;
        for(int pass = 0; pass < 16; pass++){
            double best = straightCost(res, gap);
            List<Point> replacement = null;
            for(int first = 1; first < res.size() - 2 && replacement == null; first++){
                for(int last = res.size() - 2; last > first; last--){
                    for(List<Point> connector : diagonalConnections(res.get(first), res.get(last))){
                        if(!clear(connector, blocked)) continue;
                        List<Point> candidate = new ArrayList<>(res.subList(0, first));
                        candidate.addAll(connector); candidate.addAll(res.subList(last + 1, res.size()));
                        candidate = simplify(candidate);
                        double cost = straightCost(candidate, gap);
                        if(cost >= best - 1.0E-7D) continue;
                        replacement = candidate; best = cost;
                    }
                    if(replacement != null) break;
                }
            }
            if(replacement == null) return res;
            res = replacement;
        }
        return res;
    }

    private static List<List<Point>> diagonalConnections(Point a, Point b){
        double dx = b.x() - a.x(), dy = b.y() - a.y(), sx = Math.signum(dx), sy = Math.signum(dy);
        double diagonal = Math.min(Math.abs(dx), Math.abs(dy)), x = Math.abs(dx) - diagonal, y = Math.abs(dy) - diagonal;
        return List.of(
                simplify(List.of(a, new Point(a.x() + sx * x / 2, a.y() + sy * y / 2), new Point(b.x() - sx * x / 2, b.y() - sy * y / 2), b)),
                simplify(List.of(a, new Point(a.x() + sx * diagonal / 2, a.y() + sy * diagonal / 2),
                        new Point(b.x() - sx * diagonal / 2, b.y() - sy * diagonal / 2), b)),
                simplify(List.of(a, new Point(a.x() + sx * diagonal, a.y() + sy * diagonal), b)),
                simplify(List.of(a, new Point(b.x() - sx * diagonal, b.y() - sy * diagonal), b)));
    }

    private static double straightCost(List<Point> points, double gap){
        double cost = length(points) + Math.max(0, points.size() - 2) * gap * 2;
        for(int idx = 1; idx < points.size() - 1; idx++){
            Point a = points.get(idx - 1), b = points.get(idx), c = points.get(idx + 1);
            Point in = unit(b.x() - a.x(), b.y() - a.y()), out = unit(c.x() - b.x(), c.y() - b.y());
            double turn = in.x() * out.x() + in.y() * out.y();
            if(turn < -1.0E-8D) cost += gap * 16;
            else if(Math.abs(turn) < 1.0E-8D) cost += gap * 4;
        }
        return cost;
    }

    private static List<Point> middleRoute(Point a, Point b, List<Bounds> blocked, double gap){
        List<List<Point>> candidates = new ArrayList<>();
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double diagonal = Math.min(Math.abs(dx), Math.abs(dy));
        candidates.add(List.of(a, new Point(a.x() + Math.signum(dx) * diagonal, a.y() + Math.signum(dy) * diagonal), b));
        candidates.add(List.of(a, new Point(b.x() - Math.signum(dx) * diagonal, b.y() - Math.signum(dy) * diagonal), b));
        double midX = (a.x() + b.x()) / 2, midY = (a.y() + b.y()) / 2;
        candidates.add(List.of(a, new Point(midX, a.y()), new Point(midX, b.y()), b));
        candidates.add(List.of(a, new Point(a.x(), midY), new Point(b.x(), midY), b));
        double left = Math.min(a.x(), b.x()), right = Math.max(a.x(), b.x());
        double top = Math.min(a.y(), b.y()), bottom = Math.max(a.y(), b.y());
        Bounds corridor = new Bounds(left, top, right, bottom).inflated(gap * 8);
        List<Bounds> nearby = blocked.stream().filter(rect -> rect.intersects(corridor)).toList();
        for(Bounds rect : nearby){
            left = Math.min(left, rect.left()); right = Math.max(right, rect.right());
            top = Math.min(top, rect.top()); bottom = Math.max(bottom, rect.bottom());
        }
        for(double y : new double[]{top - gap, bottom + gap}) candidates.add(List.of(a, new Point(a.x(), y), new Point(b.x(), y), b));
        for(double x : new double[]{left - gap, right + gap}) candidates.add(List.of(a, new Point(x, a.y()), new Point(x, b.y()), b));
        List<Point> best = null;
        double cost = Double.POSITIVE_INFINITY;
        for(List<Point> candidate : candidates){
            List<Point> path = simplify(candidate);
            if(!clear(path, blocked)) continue;
            double length = length(path) + path.size() * gap * 0.5;
            if(length < cost){ best = path; cost = length; }
        }
        if(best != null) return best;
        List<Point> route = search(a, b, nearby, blocked, gap);
        if(route == null && nearby.size() != blocked.size()) route = search(a, b, blocked, blocked, gap);
        if(route != null) return route;
        return List.of(a, new Point(a.x(), top - gap), new Point(b.x(), top - gap), b);
    }

    // Search a coordinate grid only when the small set of direct and perimeter routes is blocked
    private static List<Point> search(Point a, Point b, List<Bounds> nearby, List<Bounds> blocked, double gap){
        TreeSet<Double> xSet = new TreeSet<>(), ySet = new TreeSet<>();
        xSet.add(a.x()); xSet.add(b.x()); ySet.add(a.y()); ySet.add(b.y());
        for(Bounds rect : nearby){ xSet.add(rect.left()); xSet.add(rect.right()); ySet.add(rect.top()); ySet.add(rect.bottom()); }
        xSet.add(xSet.first() - gap); xSet.add(xSet.last() + gap);
        ySet.add(ySet.first() - gap); ySet.add(ySet.last() + gap);
        double[] xs = xSet.stream().mapToDouble(Double::doubleValue).toArray();
        double[] ys = ySet.stream().mapToDouble(Double::doubleValue).toArray();
        int width = xs.length;
        if((long)width * ys.length > 1_100_000) return null;
        int count = width * ys.length;
        int start = Arrays.binarySearch(ys, a.y()) * width + Arrays.binarySearch(xs, a.x());
        int goal = Arrays.binarySearch(ys, b.y()) * width + Arrays.binarySearch(xs, b.x());
        double[] costs = new double[count]; Arrays.fill(costs, Double.POSITIVE_INFINITY);
        int[] prev = new int[count]; Arrays.fill(prev, -1);
        boolean[] visited = new boolean[count];
        PriorityQueue<Step> pending = new PriorityQueue<>(Comparator.comparingDouble(Step::score).thenComparingInt(Step::idx));
        costs[start] = 0; pending.add(new Step(start, distance(a, b)));
        while(!pending.isEmpty()){
            int idx = pending.remove().idx();
            if(visited[idx]) continue;
            if(idx == goal){
                List<Point> points = new ArrayList<>();
                for(int cursor = goal; cursor != -1; cursor = prev[cursor]) points.add(new Point(xs[cursor % width], ys[cursor / width]));
                java.util.Collections.reverse(points);
                return simplify(points);
            }
            visited[idx] = true;
            int x = idx % width, y = idx / width;
            Point pos = new Point(xs[x], ys[y]);
            for(int dir = 0; dir < 4; dir++){
                int nx = x + (dir == 0 ? -1 : dir == 1 ? 1 : 0), ny = y + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if(nx < 0 || nx >= width || ny < 0 || ny >= ys.length) continue;
                int next = ny * width + nx;
                Point point = new Point(xs[nx], ys[ny]);
                if(visited[next] || !clear(pos, point, blocked)) continue;
                double cost = costs[idx] + distance(pos, point);
                if(cost >= costs[next]) continue;
                costs[next] = cost; prev[next] = idx;
                pending.add(new Step(next, cost + distance(point, b)));
            }
        }
        return null;
    }

    private static List<Point> bevelCorners(List<Point> points, List<Bounds> blocked, double radius){
        List<Point> res = new ArrayList<>(); res.add(points.getFirst());
        for(int idx = 1; idx < points.size() - 1; idx++){
            Point prev = points.get(idx - 1), point = points.get(idx), next = points.get(idx + 1);
            Point in = unit(point.x() - prev.x(), point.y() - prev.y()), out = unit(next.x() - point.x(), next.y() - point.y());
            double turn = in.x() * out.x() + in.y() * out.y();
            double cut = Math.min(radius, Math.min(distance(prev, point), distance(point, next)) * 0.5);
            Point before = add(point, in, -cut), after = add(point, out, cut);
            if(Math.abs(turn) < 1.0E-8D && clear(before, after, blocked)){ res.add(before); res.add(after); }
            else res.add(point);
        }
        res.add(points.getLast());
        return simplify(res);
    }

    // Round the straight route's bends while retaining its long axis runs and diagonals
    private static Path roundedRoute(List<Point> points, List<Bounds> blocked, double radius){
        List<Point> res = new ArrayList<>(); res.add(points.getFirst());
        for(int idx = 1; idx < points.size() - 1; idx++){
            Point prev = points.get(idx - 1), point = points.get(idx), next = points.get(idx + 1);
            Point in = unit(point.x() - prev.x(), point.y() - prev.y()), out = unit(next.x() - point.x(), next.y() - point.y());
            double turn = in.x() * out.x() + in.y() * out.y();
            double cut = Math.min(radius, Math.min(distance(prev, point), distance(point, next)) * 0.5);
            boolean rounded = false;
            while(cut > 0.001 && Math.abs(turn) < 1 - 1.0E-8D){
                Point before = add(point, in, -cut), after = add(point, out, cut);
                var curve = new Bezier(before, add(before, in, cut * 2 / 3), add(after, out, -cut * 2 / 3), after);
                var samples = curve.sample();
                if(clear(samples, blocked)){ res.addAll(samples); rounded = true; break; }
                cut *= 0.5;
            }
            if(!rounded) res.add(point);
        }
        res.add(points.getLast());
        return new Path(simplify(res));
    }

    // Fit complete cubic spans with shared tangents through the route, adjusting handles to clear node bodies
    private static Path bezierRoute(List<Point> points, Endpoint source, Endpoint target, List<Bounds> blocked){
        int count = points.size();
        if(count == 1) return Path.fromBeziers(List.of(new Bezier(points.getFirst(), points.getFirst(), points.getFirst(), points.getFirst())));
        Point[] tangents = new Point[count];
        double[] handles = new double[count];
        tangents[0] = source.outward(); tangents[count - 1] = new Point(-target.outward().x(), -target.outward().y());
        handles[0] = distance(points.get(0), points.get(1)) * 0.45;
        handles[count - 1] = distance(points.get(count - 2), points.get(count - 1)) * 0.45;
        for(int idx = 1; idx < count - 1; idx++){
            Point prev = points.get(idx - 1), pos = points.get(idx), next = points.get(idx + 1);
            Point in = unit(pos.x() - prev.x(), pos.y() - prev.y()), out = unit(next.x() - pos.x(), next.y() - pos.y());
            tangents[idx] = unit(in.x() + out.x(), in.y() + out.y());
            if(Math.hypot(tangents[idx].x(), tangents[idx].y()) < 1.0E-8D) tangents[idx] = new Point(-in.y(), in.x());
            handles[idx] = Math.min(distance(prev, pos), distance(pos, next)) * 0.45;
        }
        Path path = null;
        for(int attempt = 0; attempt < 24; attempt++){
            List<Bezier> curves = new ArrayList<>();
            for(int idx = 0; idx < count - 1; idx++) curves.add(new Bezier(points.get(idx), add(points.get(idx), tangents[idx], handles[idx]),
                    add(points.get(idx + 1), tangents[idx + 1], -handles[idx + 1]), points.get(idx + 1)));
            path = Path.fromBeziers(curves);
            boolean[] shrink = new boolean[count];
            boolean collision = false;
            for(Bounds rect : blocked){
                int first = 1, last = path.points().size() - 1;
                if(source.owner() != null && rect.equals(source.owner().inflated(-0.01)))
                    while(first <= last && rect.contains(path.points().get(first - 1))) first++;
                if(target.owner() != null && rect.equals(target.owner().inflated(-0.01)))
                    while(last >= first && rect.contains(path.points().get(last))) last--;
                for(int idx = first; idx <= last; idx++){
                    if(!crosses(path.points().get(idx - 1), path.points().get(idx), rect)) continue;
                    int span = Arrays.binarySearch(path.curveEnds, idx);
                    if(span < 0) span = -span - 1;
                    shrink[span] = true; shrink[span + 1] = true; collision = true;
                }
            }
            if(!collision) return path;
            for(int idx = 0; idx < count; idx++) if(shrink[idx]) handles[idx] *= 0.5;
        }
        return path;
    }

    private static Bezier cubic(Point a, Point from, Point b, Point to){
        double handle = Math.max(24, Math.min(180, distance(a, b) * 0.5));
        return new Bezier(a, add(a, from, handle), add(b, to, handle), b);
    }

    // Allow socket approaches through their own clearance bands while rejecting a curve through either owner
    private static boolean clearInterior(List<Point> curve, List<Bounds> blocked, Bounds source, Bounds target, double gap){
        for(int idx = 1; idx < curve.size(); idx++){
            Point a = curve.get(idx - 1), b = curve.get(idx);
            for(Bounds rect : blocked){
                if(source != null && rect.equals(source.inflated(gap)) || target != null && rect.equals(target.inflated(gap))){
                    Bounds body = rect.inflated(-gap - 0.01);
                    if(crosses(a, b, body)) return false;
                }else if(crosses(a, b, rect)) return false;
            }
        }
        return true;
    }

    private static boolean clear(List<Point> points, List<Bounds> blocked){
        for(int idx = 1; idx < points.size(); idx++) if(!clear(points.get(idx - 1), points.get(idx), blocked)) return false;
        return true;
    }

    private static boolean clear(Point a, Point b, List<Bounds> blocked){
        for(Bounds rect : blocked) if(crosses(a, b, rect)) return false;
        return true;
    }

    // Treat travel on a rectangle boundary as clear so visibility-grid corners remain connected
    private static boolean crosses(Point a, Point b, Bounds rect){
        if(Math.max(a.x(), b.x()) <= rect.left() + 1.0E-7D || Math.min(a.x(), b.x()) >= rect.right() - 1.0E-7D
                || Math.max(a.y(), b.y()) <= rect.top() + 1.0E-7D || Math.min(a.y(), b.y()) >= rect.bottom() - 1.0E-7D) return false;
        double low = 0, high = 1;
        double[] starts = {a.x(), a.y()}, deltas = {b.x() - a.x(), b.y() - a.y()};
        double[] mins = {rect.left() + 1.0E-7D, rect.top() + 1.0E-7D}, maxs = {rect.right() - 1.0E-7D, rect.bottom() - 1.0E-7D};
        for(int axis = 0; axis < 2; axis++){
            if(Math.abs(deltas[axis]) < 1.0E-10D){ if(starts[axis] < mins[axis] || starts[axis] > maxs[axis]) return false; }
            else{
                double first = (mins[axis] - starts[axis]) / deltas[axis], last = (maxs[axis] - starts[axis]) / deltas[axis];
                low = Math.max(low, Math.min(first, last)); high = Math.min(high, Math.max(first, last));
                if(low > high) return false;
            }
        }
        return high >= low;
    }

    private static List<Point> simplify(List<Point> points){
        List<Point> res = new ArrayList<>();
        for(Point point : points){
            if(!res.isEmpty() && distance(res.getLast(), point) < 1.0E-8D) continue;
            while(res.size() > 1){
                Point a = res.get(res.size() - 2), b = res.getLast();
                double cross = (b.x() - a.x()) * (point.y() - b.y()) - (b.y() - a.y()) * (point.x() - b.x());
                double dot = (b.x() - a.x()) * (point.x() - b.x()) + (b.y() - a.y()) * (point.y() - b.y());
                if(Math.abs(cross) > 1.0E-7D || dot < 0) break;
                res.removeLast();
            }
            res.add(point);
        }
        return res;
    }

    private static double length(List<Point> points){
        double res = 0;
        for(int idx = 1; idx < points.size(); idx++) res += distance(points.get(idx - 1), points.get(idx));
        return res;
    }

    private static double distance(Point a, Point b){ return Math.hypot(b.x() - a.x(), b.y() - a.y()); }
    private static Point add(Point point, Point dir, double distance){ return new Point(point.x() + dir.x() * distance, point.y() + dir.y() * distance); }
    private static Point unit(double x, double y){ double length = Math.hypot(x, y); return length < 1.0E-8D ? new Point(0, 0) : new Point(x / length, y / length); }
    private static double clean(double val){ return Math.abs(val) < 1.0E-8D ? 0 : val; }

    // Get the distance squared
    private static double distanceSquared(double x, double y, Point start, Point end) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0D) return square(x - start.x()) + square(y - start.y());
        double amount = Math.max(0.0D, Math.min(1.0D,
                ((x - start.x()) * dx + (y - start.y()) * dy) / lengthSquared));
        return square(x - (start.x() + dx * amount)) + square(y - (start.y() + dy * amount));
    }

    // Get the square
    private static double square(double val) {
        return val * val;
    }

    // Store the point
    public record Point(double x, double y) {
    }

    // Store the segment
    public record Segment(Point start, Point end) {
        // Get the length
        public double length() {
            return distance(start, end);
        }
    }

    public record Bounds(double left, double top, double right, double bottom){
        public Bounds inflated(double amount){ return new Bounds(left - amount, top - amount, right + amount, bottom + amount); }
        public boolean contains(Point point){ return point.x() >= left && point.x() <= right && point.y() >= top && point.y() <= bottom; }
        public boolean intersects(Bounds rect){ return left <= rect.right && right >= rect.left && top <= rect.bottom && bottom >= rect.top; }
    }

    public record Endpoint(Point position, Point outward, Bounds owner){
        public Endpoint{ outward = unit(outward.x(), outward.y()); if(outward.x() == 0 && outward.y() == 0) outward = new Point(1, 0); }
        public Endpoint(Point position, Point outward){ this(position, outward, null); }
    }

    private record Step(int idx, double score){}

    // Retain the actual cubic control points independently of its render samples
    public record Bezier(Point start, Point control1, Point control2, Point end){
        public Point pointAt(double amount){
            if(amount <= 0) return start;
            if(amount >= 1) return end;
            double t = amount, u = 1 - t;
            return new Point(u * u * u * start.x() + 3 * u * u * t * control1.x() + 3 * u * t * t * control2.x() + t * t * t * end.x(),
                    u * u * u * start.y() + 3 * u * u * t * control1.y() + 3 * u * t * t * control2.y() + t * t * t * end.y());
        }

        public Point tangentAt(double amount){
            double t = Math.max(0, Math.min(1, amount)), u = 1 - t;
            return new Point(3 * u * u * (control1.x() - start.x()) + 6 * u * t * (control2.x() - control1.x()) + 3 * t * t * (end.x() - control2.x()),
                    3 * u * u * (control1.y() - start.y()) + 6 * u * t * (control2.y() - control1.y()) + 3 * t * t * (end.y() - control2.y()));
        }

        public List<Point> sample(){
            int steps = Math.max(16, Math.min(256, (int)Math.ceil((distance(start, control1) + distance(control1, control2) + distance(control2, end)) / 4)));
            List<Point> points = new ArrayList<>();
            for(int idx = 0; idx <= steps; idx++) points.add(pointAt(idx / (double)steps));
            return List.copyOf(points);
        }
    }

    // Retain one sampled path for drawing, bounds, picking and distance-based animation
    public static final class Path{
        private final List<Point> points;
        private final List<Segment> segments;
        private final double length;
        private final Bounds bounds;
        private final List<Bezier> beziers;
        private final int[] curveEnds;

        public Path(List<Point> points){
            this(points, List.of(), new int[0]);
        }

        private Path(List<Point> points, List<Bezier> beziers, int[] curveEnds){
            if(points.isEmpty()) throw new IllegalArgumentException("A wire path requires a point");
            this.points = List.copyOf(points);
            this.beziers = List.copyOf(beziers); this.curveEnds = curveEnds.clone();
            List<Segment> parts = new ArrayList<>();
            double total = 0, left = points.getFirst().x(), right = left, top = points.getFirst().y(), bottom = top;
            for(int idx = 0; idx < points.size(); idx++){
                Point point = points.get(idx);
                left = Math.min(left, point.x()); right = Math.max(right, point.x()); top = Math.min(top, point.y()); bottom = Math.max(bottom, point.y());
                if(idx > 0){ Segment part = new Segment(points.get(idx - 1), point); parts.add(part); total += part.length(); }
            }
            segments = List.copyOf(parts); length = total; bounds = new Bounds(left, top, right, bottom);
        }

        public List<Point> points(){ return points; }
        public List<Segment> segments(){ return segments; }
        public double length(){ return length; }
        public Bounds bounds(){ return bounds; }
        public List<Bezier> beziers(){ return beziers; }

        public static Path fromBeziers(List<Bezier> curves){
            if(curves.isEmpty()) throw new IllegalArgumentException("A Bezier path requires a curve");
            List<Point> points = new ArrayList<>();
            int[] ends = new int[curves.size()];
            for(int idx = 0; idx < curves.size(); idx++){
                Bezier curve = curves.get(idx);
                if(idx > 0 && distance(curves.get(idx - 1).end(), curve.start()) > 1.0E-8D)
                    throw new IllegalArgumentException("Bezier spans must share their endpoints");
                List<Point> samples = curve.sample();
                points.addAll(idx == 0 ? samples : samples.subList(1, samples.size())); ends[idx] = points.size() - 1;
            }
            return new Path(points, curves, ends);
        }

        public Point pointAt(double amount){
            if(amount <= 0) return points.getFirst();
            if(amount >= 1) return points.getLast();
            double remaining = Math.max(0, Math.min(1, amount)) * length;
            for(Segment part : segments){
                if(remaining <= part.length()){
                    double local = part.length() == 0 ? 0 : remaining / part.length();
                    return new Point(part.start().x() + (part.end().x() - part.start().x()) * local,
                            part.start().y() + (part.end().y() - part.start().y()) * local);
                }
                remaining -= part.length();
            }
            return points.getLast();
        }

        public double distanceSquared(double x, double y){
            double closest = Double.POSITIVE_INFINITY;
            for(Segment part : segments) closest = Math.min(closest, GraphWireGeometry.distanceSquared(x, y, part.start(), part.end()));
            return segments.isEmpty() ? square(x - points.getFirst().x()) + square(y - points.getFirst().y()) : closest;
        }
    }
}
