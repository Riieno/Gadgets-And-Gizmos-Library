package com.rieno.gadgetsandgizmos.lib.graph.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry.Bounds;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry.Endpoint;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry.Mode;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry.Path;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry.Segment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Retain unaffected wires and plan detours only after a node actually crosses a wire
public final class GraphWireRouteCache{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final Map<String, Bounds> nodes = new LinkedHashMap<>();
    private final Map<String, Routed> routes = new LinkedHashMap<>();
    private final Map<String, Path> paths = new LinkedHashMap<>();
    private final Map<String, Path> view = Collections.unmodifiableMap(paths);
    private final BoundsIndex nodeIndex = new BoundsIndex(), wireIndex = new BoundsIndex();
    private Mode mode = Mode.STRAIGHT;
    private double clearance = 8;
    private boolean avoiding;
    private long revision, previewRevision = -1;
    private Connection preview;
    private Path previewPath;
    private int detours;
    private Work lastUpdate = new Work(0, 0);

    public record Connection(Endpoint source, Endpoint target){
        public Connection{ Objects.requireNonNull(source); Objects.requireNonNull(target); }
    }

    // Report only the routes updated and detours planned by the most recent snapshot update
    public record Work(int updated, int detours){}

    private record Routed(Connection connection, Path base, Path path){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public Map<String, Path> paths(){ return view; }
    public Work lastUpdate(){ return lastUpdate; }

    // Detect changed geometry while preserving the exact cached paths of unrelated wires
    public void update(Map<String, Bounds> bounds, Map<String, Connection> connections, Mode mode,
                       double clearance, boolean avoiding){
        Objects.requireNonNull(mode);
        if(!Double.isFinite(clearance)) throw new IllegalArgumentException("Wire clearance must be finite");
        double gap = Math.max(4, clearance);
        boolean geometryChanged = this.mode != mode || this.clearance != gap;
        Set<String> dirty = new LinkedHashSet<>();
        if(geometryChanged || this.avoiding != avoiding) dirty.addAll(connections.keySet());
        for(String id : new ArrayList<>(routes.keySet())) if(!connections.containsKey(id)){
            routes.remove(id); paths.remove(id); wireIndex.remove(id);
        }
        boolean moved = false;
        Set<String> ids = new LinkedHashSet<>(nodes.keySet()); ids.addAll(bounds.keySet());
        for(String id : ids){
            Bounds prev = nodes.get(id), next = bounds.get(id);
            if(Objects.equals(prev, next)) continue;
            moved = true;
            if(avoiding){ affected(prev, true, dirty); affected(next, false, dirty); }
            if(next == null){ nodeIndex.remove(id); nodes.remove(id); }
            else{ nodeIndex.put(id, List.of(next)); nodes.put(id, next); }
        }
        this.mode = mode; this.clearance = gap; this.avoiding = avoiding;
        if(moved || geometryChanged || !dirty.isEmpty()) revision++;
        detours = 0;
        for(var entry : connections.entrySet()){
            var prev = routes.get(entry.getKey());
            boolean changed = geometryChanged || prev == null || !prev.connection().equals(entry.getValue());
            if(!changed && !dirty.contains(entry.getKey())) continue;
            Path base = changed ? base(entry.getValue()) : prev.base();
            Path path = avoiding ? avoid(entry.getValue(), base, changed ? null : prev.path()) : base;
            routes.put(entry.getKey(), new Routed(entry.getValue(), base, path));
            paths.put(entry.getKey(), path);
            wireIndex.put(entry.getKey(), spans(base, path));
            dirty.add(entry.getKey());
        }
        lastUpdate = new Work(dirty.size(), detours);
    }

    // Use the same local collision checks for a wire being dragged from a socket
    public Path preview(Endpoint source, Endpoint target){
        Connection next = new Connection(source, target);
        if(next.equals(preview) && previewRevision == revision) return previewPath;
        Path base = base(next);
        previewPath = avoiding ? avoid(next, base, null) : base;
        preview = next; previewRevision = revision;
        return previewPath;
    }

    public void clear(){
        nodes.clear(); routes.clear(); paths.clear(); nodeIndex.clear(); wireIndex.clear();
        preview = null; previewPath = null; previewRevision = -1; revision++;
        lastUpdate = new Work(0, 0);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private Path base(Connection wire){
        return GraphWireGeometry.route(wire.source(), wire.target(), List.of(), mode, clearance, false);
    }

    // The old position restores obsolete detours; the new position finds newly crossed wires
    private void affected(Bounds rect, boolean previous, Set<String> dirty){
        if(rect == null) return;
        for(String id : wireIndex.query(rect)){
            Routed route = routes.get(id);
            if(route != null && (previous && route.base().intersects(rect) || route.path().intersects(rect))) dirty.add(id);
        }
    }

    private Set<String> collisions(Connection wire, Path path){
        Set<String> candidates = new LinkedHashSet<>();
        for(Segment part : path.segments()) candidates.addAll(nodeIndex.query(span(part)));
        candidates.removeIf(id -> {
            Bounds rect = nodes.get(id);
            return rect == null || rect.equals(wire.source().owner()) || rect.equals(wire.target().owner()) || !path.intersects(rect);
        });
        return candidates;
    }

    private Path avoid(Connection wire, Path base, Path previous){
        Set<String> blocked = collisions(wire, base);
        if(blocked.isEmpty()) return base;
        if(previous != null && collisions(wire, previous).isEmpty()) return previous;
        Bounds corridor = base.bounds().inflated(clearance * 8);
        for(String id : nodeIndex.query(corridor)) if(nodes.get(id).intersects(corridor)) blocked.add(id);
        while(true){
            List<Bounds> nearby = new ArrayList<>();
            if(wire.source().owner() != null) nearby.add(wire.source().owner());
            if(wire.target().owner() != null && !nearby.contains(wire.target().owner())) nearby.add(wire.target().owner());
            for(String id : blocked) if(!nearby.contains(nodes.get(id))) nearby.add(nodes.get(id));
            detours++;
            Path path = GraphWireGeometry.route(wire.source(), wire.target(), nearby, mode, clearance);
            Set<String> crossed = collisions(wire, path);
            crossed.removeAll(blocked);
            if(crossed.isEmpty()) return path;
            blocked.addAll(crossed);
        }
    }

    private static List<Bounds> spans(Path base, Path path){
        List<Bounds> res = new ArrayList<>();
        for(Segment part : base.segments()) res.add(span(part));
        if(path != base) for(Segment part : path.segments()) res.add(span(part));
        return res;
    }

    private static Bounds span(Segment part){
        return new Bounds(Math.min(part.start().x(), part.end().x()), Math.min(part.start().y(), part.end().y()),
                Math.max(part.start().x(), part.end().x()), Math.max(part.start().y(), part.end().y()));
    }

    // Bucket sampled segments rather than whole wire rectangles so long diagonal wires stay local
    private static final class BoundsIndex{
        private static final double CELL_SIZE = 256;
        private static final int MAX_CELLS = 4096;
        private final Map<Cell, Set<String>> cells = new HashMap<>();
        private final Map<String, Set<Cell>> memberships = new HashMap<>();
        private final Set<String> large = new LinkedHashSet<>();
        private record Cell(long x, long y){}

        private void put(String id, Collection<Bounds> bounds){
            remove(id);
            Set<Cell> covered = new LinkedHashSet<>();
            for(Bounds rect : bounds){
                if(!collect(rect, covered)){ large.add(id); return; }
            }
            memberships.put(id, covered);
            for(Cell cell : covered) cells.computeIfAbsent(cell, key -> new LinkedHashSet<>()).add(id);
        }

        private void remove(String id){
            large.remove(id);
            Set<Cell> covered = memberships.remove(id);
            if(covered == null) return;
            for(Cell cell : covered){
                Set<String> ids = cells.get(cell);
                ids.remove(id); if(ids.isEmpty()) cells.remove(cell);
            }
        }

        private Set<String> query(Bounds rect){
            Set<Cell> covered = new LinkedHashSet<>();
            Set<String> res = new LinkedHashSet<>(large);
            if(!collect(rect, covered)){ res.addAll(memberships.keySet()); return res; }
            for(Cell cell : covered) res.addAll(cells.getOrDefault(cell, Set.of()));
            return res;
        }

        private static boolean collect(Bounds rect, Set<Cell> covered){
            long left = (long)Math.floor(rect.left() / CELL_SIZE), right = (long)Math.floor(rect.right() / CELL_SIZE);
            long top = (long)Math.floor(rect.top() / CELL_SIZE), bottom = (long)Math.floor(rect.bottom() / CELL_SIZE);
            if(!Double.isFinite(rect.left()) || !Double.isFinite(rect.top()) || !Double.isFinite(rect.right()) || !Double.isFinite(rect.bottom())
                    || left == Long.MIN_VALUE || right == Long.MAX_VALUE || top == Long.MIN_VALUE || bottom == Long.MAX_VALUE
                    || right < left || bottom < top || (double)right - left >= MAX_CELLS || (double)bottom - top >= MAX_CELLS
                    || ((double)right - left + 1) * ((double)bottom - top + 1) > MAX_CELLS) return false;
            for(long x = left; x <= right; x++) for(long y = top; y <= bottom; y++){
                covered.add(new Cell(x, y)); if(covered.size() > MAX_CELLS) return false;
            }
            return true;
        }

        private void clear(){ cells.clear(); memberships.clear(); large.clear(); }
    }
}
