package com.rieno.gadgetsandgizmos.lib.shipping;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Choose the next valid Ship Dock stop without depending on the live controller runtime
public final class ShipDockScheduler {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long REQUEST_TIMEOUT_TICKS = 100L;
    private static final double ARRIVAL_READY_DISTANCE = 16.0D;
    private static final long ARRIVAL_READY_ETA_TICKS = 40L;
    private static final Map<MinecraftServer, ShipDockScheduler> INSTANCES =
            new WeakHashMap<>();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked requests
    private final Map<RequestKey, DockRequest> requests = new HashMap<>();
    // Next connector offsets
    private final Map<UUID, Integer> nextConnectorOffsets = new HashMap<>();
    // Next sequence
    private long nextSequence;
    // Tracks whether ship dock scheduler is shutting down
    private boolean shuttingDown;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship dock scheduler
    public ShipDockScheduler() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the ship dock scheduler value
    public static synchronized ShipDockScheduler get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ignored -> new ShipDockScheduler());
    }

    // Shut down the ship dock scheduler
    public static synchronized void shutdown(MinecraftServer server) {
        if (server == null) {
            return;
        }
        INSTANCES.computeIfAbsent(server, ignored -> new ShipDockScheduler())
                .clearForShutdown();
    }

    // Finish the shutdown
    public static synchronized void finishShutdown(MinecraftServer server) {
        if (server != null) {
            INSTANCES.remove(server);
        }
    }

    /**
     * Resolve a queue holding point and move it outside retained-route
     * corridors. This changes only parking geometry; leases and route
     * ownership remain unchanged.
     */
    public static Vec3 resolveHoldingPosition(
            Vec3 anchor,
            Vec3 outwardDirection,
            HoldingPlacement placement,
            double verticalOffset,
            Collection<List<Vec3>> retainedRoutes,
            double routeClearance
    ) {
        Vec3 origin = finite(anchor);
        Vec3 outward = horizontalUnit(
                outwardDirection, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 lateral = new Vec3(-outward.z, 0.0D, outward.x);
        HoldingPlacement safe = placement == null
                ? HoldingPlacement.NONE : placement;
        Vec3 preferredSide = lateral.scale(safe.lateral() < 0.0D ? -1.0D : 1.0D);
        Vec3 resolved = origin
                .add(outward.scale(safe.outward()))
                .add(lateral.scale(safe.lateral()))
                .add(0.0D, Double.isFinite(verticalOffset) ? verticalOffset : 0.0D, 0.0D);
        double clearance = Double.isFinite(routeClearance)
                ? Math.max(0.0D, routeClearance) : 0.0D;
        Collection<List<Vec3>> routes = retainedRoutes == null
                ? List.of() : retainedRoutes;
        for (int pass = 0; pass < 8 && clearance > 0.0D; pass++) {
            RouteClearance nearest = nearestRoute(resolved, routes);
            if (!nearest.found() || nearest.distance() + 1.0E-6D >= clearance) break;
            Vec3 normal = new Vec3(
                    -nearest.direction().z, 0.0D, nearest.direction().x);
            if (normal.lengthSqr() <= 1.0E-12D) {
                normal = horizontalUnit(
                        resolved.subtract(nearest.position()), preferredSide);
            }
            double side = normal.dot(resolved.subtract(nearest.position()));
            if (Math.abs(side) <= 1.0E-8D) side = normal.dot(preferredSide);
            resolved = resolved.add(normal.scale(Math.copySign(
                    clearance - nearest.distance() + 0.25D,
                    Math.abs(side) <= 1.0E-8D ? 1.0D : side)));
        }
        return resolved;
    }

    // Request the ship dock scheduler
    public synchronized Lease request(
            UUID requester,
            List<DockSlot> candidateDocks,
            Set<DockSlot> occupiedDocks,
            @Nullable DockSlot ownedDock,
            long gameTime
    ) {
        return request(RequestKey.primary(requester), candidateDocks, occupiedDocks,
                ownedDock, RequestPriority.DEFAULT, VesselEnvelope.DEFAULT, gameTime);
    }

    // Request the ship dock scheduler
    public synchronized Lease request(
            UUID requester,
            List<DockSlot> candidateDocks,
            Set<DockSlot> occupiedDocks,
            @Nullable DockSlot ownedDock,
            RequestPriority priority,
            long gameTime
    ) {
        return request(RequestKey.primary(requester), candidateDocks, occupiedDocks,
                ownedDock, priority, VesselEnvelope.DEFAULT, gameTime);
    }

    // Request one named ship resource channel
    public synchronized Lease request(
            RequestKey requester,
            List<DockSlot> candidateDocks,
            Set<DockSlot> occupiedDocks,
            @Nullable DockSlot ownedDock,
            RequestPriority priority,
            VesselEnvelope vessel,
            long gameTime
    ) {
        if (shuttingDown || requester == null || requester.ownerId() == null) {
            return Lease.NONE;
        }
        cleanup(gameTime);
        List<DockSlot> candidates = candidateDocks == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(candidateDocks));
        Set<DockSlot> occupied = occupiedDocks == null ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(occupiedDocks));
        RequestPriority requestPriority = priority == null ? RequestPriority.DEFAULT : priority;
        VesselEnvelope requestVessel = vessel == null ? VesselEnvelope.DEFAULT : vessel;

        DockRequest req = requests.get(requester);
        if (req == null) {
            req = new DockRequest(
                    requester, nextSequence++, candidates, occupied, ownedDock,
                    requestPriority, requestVessel, gameTime, gameTime);
            requests.put(requester, req);
        } else {
            req.lastSeen = gameTime;
            req.ownedDock = ownedDock;
            req.occupiedDocks = occupied;
            req.priority = requestPriority;
            req.vessel = requestVessel;
            if (!req.candidates.equals(candidates)) {
                if (req.assignedDock != null
                        && !candidates.contains(req.assignedDock)) {
                    releaseAssignment(req);
                }
                req.candidates = candidates;
            }
        }
        rebalance();
        return req.assignedDock == null
                ? new Lease(null, null, queuePosition(req), holdingPlacement(req))
                : new Lease(req.assignedDock.dockId(), req.assignedDock.connectorKey(),
                0, HoldingPlacement.NONE);
    }

    // Update the ship dock scheduler
    public synchronized void heartbeat(UUID requester, long gameTime) {
        heartbeat(RequestKey.primary(requester), RequestPriority.DEFAULT, gameTime);
    }

    // Update the ship dock scheduler
    public synchronized void heartbeat(
            UUID requester,
            RequestPriority priority,
            long gameTime
    ) {
        heartbeat(RequestKey.primary(requester), priority, gameTime);
    }

    // Update one named ship resource channel
    public synchronized void heartbeat(
            RequestKey requester,
            RequestPriority priority,
            long gameTime
    ) {
        if (shuttingDown) {
            return;
        }
        cleanup(gameTime);
        DockRequest req = requests.get(requester);
        if (req != null) {
            req.lastSeen = gameTime;
            req.priority = priority == null ? RequestPriority.DEFAULT : priority;
            rebalance();
        }
    }

    // Release the ship dock scheduler
    public synchronized void release(UUID requester) {
        if (requester == null) {
            return;
        }
        List<RequestKey> owned = requests.keySet().stream()
                .filter(key -> requester.equals(key.ownerId()))
                .toList();
        for (RequestKey key : owned) {
            DockRequest req = requests.remove(key);
            if (req != null) {
                releaseAssignment(req);
            }
        }
        if (!owned.isEmpty()) {
            rebalance();
        }
    }

    // Release one named ship resource channel
    public synchronized void release(RequestKey requester) {
        DockRequest req = requests.remove(requester);
        if (req != null) {
            releaseAssignment(req);
            rebalance();
        }
    }

    // Remove the dock
    public synchronized void removeDock(UUID dockId) {
        if (dockId == null) {
            return;
        }
        for (DockRequest req : requests.values()) {
            if (req.assignedDock != null && req.assignedDock.dockId().equals(dockId)) {
                releaseAssignment(req);
            }
        }
        for (DockRequest req : requests.values()) {
            req.candidates = req.candidates.stream()
                    .filter(candidate -> !candidate.dockId().equals(dockId))
                    .toList();
            req.occupiedDocks = req.occupiedDocks.stream()
                    .filter(candidate -> !candidate.dockId().equals(dockId))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        nextConnectorOffsets.remove(dockId);
        rebalance();
    }

    // Clear the shutdown
    private synchronized void clearForShutdown() {
        shuttingDown = true;
        requests.clear();
        nextConnectorOffsets.clear();
    }

    // Clean up the ship dock scheduler
    private void cleanup(long gameTime) {
        List<RequestKey> expired = requests.values().stream()
                .filter(req -> gameTime < req.lastSeen
                        || gameTime - req.lastSeen > REQUEST_TIMEOUT_TICKS)
                .map(req -> req.requester)
                .toList();
        for (RequestKey requester : expired) {
            DockRequest req = requests.remove(requester);
            if (req != null) {
                releaseAssignment(req);
            }
        }
        if (!expired.isEmpty()) {
            rebalance();
        }
    }

    // Rebalance the ship dock scheduler
    private void rebalance() {
        List<DockRequest> ordered = orderedRequests();
        Set<DockSlot> claimedOwnedSlots = new HashSet<>();
        for (DockRequest req : ordered) {
            DockSlot ownedSlot = req.ownedDock != null
                    && req.candidates.contains(req.ownedDock) ? req.ownedDock : null;
            if (ownedSlot == null || claimedOwnedSlots.stream().anyMatch(slot -> conflicts(slot, ownedSlot))) {
                continue;
            }
            claimedOwnedSlots.add(ownedSlot);
            for (RequestKey previousRequester : conflictingAssignments(ownedSlot)) {
                if (previousRequester.equals(req.requester)) {
                    continue;
                }
                DockRequest prev = requests.get(previousRequester);
                if (prev != null) {
                    releaseAssignment(prev);
                }
            }
            if (!ownedSlot.equals(req.assignedDock)) {
                releaseAssignment(req);
                assign(req, ownedSlot);
            }
        }
        for (DockRequest req : ordered) {
            if (req.assignedDock == null) {
                continue;
            }
            boolean requesterOwnsDock = req.assignedDock.equals(req.ownedDock);
            boolean blocked = isExternallyOccupied(req.assignedDock) && !requesterOwnsDock;
            if (blocked || !req.candidates.contains(req.assignedDock)
                    || assignmentHeldByOther(req.assignedDock, req.requester)) {
                releaseAssignment(req);
                continue;
            }
            if (!isCommitted(req) && shouldYieldAssignment(req, ordered)) {
                releaseAssignment(req);
            }
        }
        for (DockRequest req : ordered) {
            if (req.assignedDock != null) {
                continue;
            }
            for (DockSlot candidate : assignmentOrder(req.candidates)) {
                boolean requesterOwnsDock = candidate.equals(req.ownedDock);
                if (assignmentHeldByOther(candidate, req.requester)
                        || isExternallyOccupied(candidate) && !requesterOwnsDock) {
                    continue;
                }
                assign(req, candidate);
                break;
            }
        }
    }

    // Check if the externally is occupied
    private boolean isExternallyOccupied(DockSlot candidate) {
        return requests.values().stream()
                .flatMap(req -> req.occupiedDocks.stream())
                .anyMatch(occupied -> conflicts(candidate, occupied));
    }

    // Check if another requester holds the assignment
    private boolean assignmentHeldByOther(DockSlot candidate, RequestKey requester) {
        return requests.values().stream()
                .filter(req -> !req.requester.equals(requester))
                .map(req -> req.assignedDock)
                .anyMatch(assigned -> assigned != null && conflicts(candidate, assigned));
    }

    // Get the conflicting assignments
    private Set<RequestKey> conflictingAssignments(DockSlot candidate) {
        Set<RequestKey> owners = new HashSet<>();
        for (DockRequest req : requests.values()) {
            if (req.assignedDock != null && conflicts(candidate, req.assignedDock)) {
                owners.add(req.requester);
            }
        }
        return owners;
    }

    // Check if the dock slots conflict
    private static boolean conflicts(DockSlot first, DockSlot second) {
        if (first.connectorKey() != null && second.connectorKey() != null) {
            return first.connectorKey().equals(second.connectorKey());
        }
        return first.dockId().equals(second.dockId());
    }

    // Queue the position
    private int queuePosition(DockRequest target) {
        int pos = 1;
        for (DockRequest req : orderedRequests()) {
            if (req == target) {
                return pos;
            }
            if (req.assignedDock != null && !req.assignedDock.equals(target.assignedDock)
                    && target.candidates.stream().anyMatch(targetCandidate ->
                    conflicts(req.assignedDock, targetCandidate))) {
                pos++;
            } else if (req.assignedDock == null && req.candidates.stream()
                    .anyMatch(candidate -> target.candidates.stream().anyMatch(targetCandidate ->
                            conflicts(candidate, targetCandidate)))) {
                pos++;
            }
        }
        return pos;
    }

    // Get the size-aware holding placement
    private HoldingPlacement holdingPlacement(DockRequest target) {
        List<DockRequest> related = orderedRequests().stream()
                .filter(req -> requestsConflict(req, target))
                .toList();
        double radius = related.stream()
                .map(req -> req.vessel)
                .mapToDouble(VesselEnvelope::horizontalRadius)
                .max().orElse(target.vessel.horizontalRadius());
        double height = related.stream()
                .map(req -> req.vessel)
                .mapToDouble(VesselEnvelope::height)
                .max().orElse(target.vessel.height());
        int slot = Math.max(0, queuePosition(target) - 1);
        int row = slot / 2;
        int lane = slot % 2 == 0 ? -1 : 1;
        double horizontalSpacing = Math.max(8.0D, radius * 2.0D + 4.0D);
        double verticalSpacing = Math.max(4.0D, height + 2.0D);
        return new HoldingPlacement(
                12.0D + radius + row * horizontalSpacing,
                lane * horizontalSpacing,
                row * verticalSpacing);
    }

    // Check if two requests compete for any resource
    private static boolean requestsConflict(DockRequest first, DockRequest second) {
        return first.candidates.stream().anyMatch(firstCandidate ->
                second.candidates.stream().anyMatch(secondCandidate ->
                        conflicts(firstCandidate, secondCandidate)));
    }

    // Find the nearest horizontal point on any retained route.
    private static RouteClearance nearestRoute(
            Vec3 position,
            Collection<List<Vec3>> routes
    ) {
        RouteClearance selected = RouteClearance.none();
        for (List<Vec3> route : routes) {
            if (route == null || route.isEmpty()) continue;
            Vec3 previous = finite(route.getFirst());
            if (route.size() == 1) {
                double distance = horizontal(position.subtract(previous)).length();
                if (distance < selected.distance()) {
                    selected = new RouteClearance(
                            previous, Vec3.ZERO, distance, true);
                }
                continue;
            }
            for (int index = 1; index < route.size(); index++) {
                Vec3 next = finite(route.get(index));
                Vec3 segment = horizontal(next.subtract(previous));
                double lengthSqr = segment.lengthSqr();
                double progress = lengthSqr <= 1.0E-12D ? 1.0D
                        : Math.max(0.0D, Math.min(1.0D,
                        horizontal(position.subtract(previous)).dot(segment) / lengthSqr));
                Vec3 projection = previous.add(next.subtract(previous).scale(progress));
                double distance = horizontal(position.subtract(projection)).length();
                if (distance < selected.distance()) {
                    selected = new RouteClearance(
                            projection,
                            lengthSqr <= 1.0E-12D ? Vec3.ZERO : segment.normalize(),
                            distance, true);
                }
                previous = next;
            }
        }
        return selected;
    }

    private static Vec3 finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z)
                ? value : Vec3.ZERO;
    }

    private static Vec3 horizontal(Vec3 value) {
        Vec3 safe = finite(value);
        return new Vec3(safe.x, 0.0D, safe.z);
    }

    private static Vec3 horizontalUnit(Vec3 value, Vec3 fallback) {
        Vec3 safe = horizontal(value);
        if (safe.lengthSqr() > 1.0E-12D) return safe.normalize();
        safe = horizontal(fallback);
        return safe.lengthSqr() > 1.0E-12D
                ? safe.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    // Get the ordered requests
    private List<DockRequest> orderedRequests() {
        List<DockRequest> ordered = new ArrayList<>(requests.values());
        ordered.sort(Comparator
                .comparing((DockRequest req) -> !isCommitted(req))
                .thenComparing(req -> !req.priority.readyToDock())
                .thenComparingDouble(req -> req.priority.distanceBlocks())
                .thenComparingLong(req -> req.sequence)
                .thenComparingLong(req -> req.priority.etaTicks() < 0
                        ? Long.MAX_VALUE : req.priority.etaTicks())
                .thenComparing(req -> req.requester.ownerId())
                .thenComparing(req -> req.requester.channel()));
        return ordered;
    }

    // Check if this is committed
    private boolean isCommitted(DockRequest req) {
        return req.ownedDock != null || req.priority.committed();
    }

    // Check if this should yield assignment
    private boolean shouldYieldAssignment(DockRequest req, List<DockRequest> ordered) {
        int requestIndex = ordered.indexOf(req);
        if (requestIndex < 0) {
            return false;
        }
        for (int idx = 0; idx < requestIndex; idx++) {
            DockRequest earlier = ordered.get(idx);
            if (earlier.assignedDock != null && !conflicts(
                    earlier.assignedDock, req.assignedDock)) {
                continue;
            }
            if (earlier.assignedDock == null
                    && hasAvailableAlternative(earlier, req.assignedDock)) {
                continue;
            }
            if (earlier.candidates.stream().anyMatch(candidate ->
                    conflicts(candidate, req.assignedDock))) {
                return true;
            }
        }
        return false;
    }

    // Check if this has available alternative
    private boolean hasAvailableAlternative(DockRequest req, DockSlot blockedSlot) {
        return req.candidates.stream()
                .filter(candidate -> !conflicts(candidate, blockedSlot))
                .anyMatch(candidate -> !assignmentHeldByOther(candidate, req.requester)
                        && (!isExternallyOccupied(candidate)
                        || candidate.equals(req.ownedDock)));
    }

    // Get the assignment order
    private List<DockSlot> assignmentOrder(List<DockSlot> candidates) {
        if (candidates == null || candidates.size() < 2) {
            return candidates == null ? List.of() : candidates;
        }
        Map<UUID, List<DockSlot>> byDock = new java.util.LinkedHashMap<>();
        for (DockSlot candidate : candidates) {
            byDock.computeIfAbsent(candidate.dockId(), ignored -> new ArrayList<>())
                    .add(candidate);
        }
        List<DockSlot> ordered = new ArrayList<>(candidates.size());
        for (Map.Entry<UUID, List<DockSlot>> entry : byDock.entrySet()) {
            List<DockSlot> dockSlots = entry.getValue();
            int offset = Math.floorMod(
                    nextConnectorOffsets.getOrDefault(entry.getKey(), 0), dockSlots.size());
            for (int idx = 0; idx < dockSlots.size(); idx++) {
                ordered.add(dockSlots.get((offset + idx) % dockSlots.size()));
            }
        }
        return ordered;
    }

    // Assign the ship dock scheduler
    private void assign(DockRequest req, DockSlot dock) {
        req.assignedDock = dock;
        List<DockSlot> matching = req.candidates.stream()
                .filter(candidate -> candidate.dockId().equals(dock.dockId()))
                .toList();
        int assignedIndex = matching.indexOf(dock);
        if (matching.size() > 1 && assignedIndex >= 0) {
            nextConnectorOffsets.put(dock.dockId(),
                    Math.floorMod(assignedIndex + 1, matching.size()));
        }
    }

    // Release the assignment
    private void releaseAssignment(DockRequest req) {
        if (req.assignedDock != null) {
            req.assignedDock = null;
        }
    }

    // Store the dock slot
    public record DockSlot(UUID dockId, @Nullable String connectorKey) {
        // Initialize the dock slot
        public DockSlot {
            if (dockId == null) {
                throw new IllegalArgumentException("Dock id is required");
            }
            connectorKey = connectorKey == null || connectorKey.isBlank() ? null : connectorKey;
        }

        // Create a named resource slot
        public static DockSlot resource(UUID dockId, String resourceKey) {
            return new DockSlot(dockId, resourceKey);
        }

        // Get the generic resource key
        public @Nullable String resourceKey() {
            return connectorKey;
        }
    }

    // Identify one independent reservation channel owned by a ship
    public record RequestKey(UUID ownerId, String channel) {
        private static final String PRIMARY_CHANNEL = "dock";

        // Initialize the request key
        public RequestKey {
            if (ownerId == null) {
                throw new IllegalArgumentException("Owner id is required");
            }
            channel = channel == null || channel.isBlank() ? PRIMARY_CHANNEL : channel.trim();
        }

        // Create the primary request key
        public static RequestKey primary(UUID ownerId) {
            return ownerId == null ? null : new RequestKey(ownerId, PRIMARY_CHANNEL);
        }
    }

    // Store the conservative vessel envelope used for berths and queue spacing
    public record VesselEnvelope(double horizontalRadius, double height, double bottomOffset) {
        private static final VesselEnvelope DEFAULT = new VesselEnvelope(1.0D, 1.0D, 0.5D);

        // Initialize the vessel envelope
        public VesselEnvelope {
            horizontalRadius = finitePositive(horizontalRadius, 1.0D);
            height = finitePositive(height, 1.0D);
            bottomOffset = Double.isFinite(bottomOffset)
                    ? Math.max(0.0D, bottomOffset) : height * 0.5D;
        }

        // Check if this vessel fits inside an axis-aligned zone
        public boolean fits(double width, double zoneHeight, double depth, double clearance) {
            double margin = Double.isFinite(clearance) ? Math.max(0.0D, clearance) : 0.0D;
            double diameter = horizontalRadius * 2.0D + margin * 2.0D;
            return Double.isFinite(width) && Double.isFinite(zoneHeight)
                    && Double.isFinite(depth) && width >= diameter && depth >= diameter
                    && zoneHeight + 1.0E-6D >= height + margin * 2.0D;
        }

        // Check if this vessel fits inside a horizontal berth footprint
        public boolean fitsFootprint(double width, double depth, double clearance) {
            double margin = Double.isFinite(clearance) ? Math.max(0.0D, clearance) : 0.0D;
            double diameter = horizontalRadius * 2.0D + margin * 2.0D;
            return Double.isFinite(width) && Double.isFinite(depth)
                    && width >= diameter && depth >= diameter;
        }

        // Normalize one positive finite value
        private static double finitePositive(double val, double fallback) {
            return Double.isFinite(val) ? Math.max(0.01D, val) : fallback;
        }
    }

    // Store offsets from a dock approach to a safe queue location
    public record HoldingPlacement(double outward, double lateral, double vertical) {
        private static final HoldingPlacement NONE = new HoldingPlacement(0.0D, 0.0D, 0.0D);

        // Initialize the holding placement
        public HoldingPlacement {
            outward = Double.isFinite(outward) ? Math.max(0.0D, outward) : 0.0D;
            lateral = Double.isFinite(lateral) ? lateral : 0.0D;
            vertical = Double.isFinite(vertical) ? Math.max(0.0D, vertical) : 0.0D;
        }
    }

    // Store one nearest route point used only to clear a holding location.
    private record RouteClearance(
            Vec3 position,
            Vec3 direction,
            double distance,
            boolean found
    ) {
        private static RouteClearance none() {
            return new RouteClearance(
                    Vec3.ZERO, Vec3.ZERO, Double.POSITIVE_INFINITY, false);
        }
    }

    // Store the request priority
    public record RequestPriority(
            double distanceBlocks,
            long etaTicks,
            boolean committed
    ) {
        private static final RequestPriority DEFAULT =
                new RequestPriority(Double.POSITIVE_INFINITY, -1L, false);

        // Initialize the request priority
        public RequestPriority {
            distanceBlocks = Double.isFinite(distanceBlocks)
                    ? Math.max(0.0D, distanceBlocks) : Double.POSITIVE_INFINITY;
            etaTicks = Math.max(-1L, etaTicks);
        }

        // Check if this is ready to dock
        public boolean readyToDock() {
            return committed || distanceBlocks <= ARRIVAL_READY_DISTANCE
                    || etaTicks >= 0L && etaTicks <= ARRIVAL_READY_ETA_TICKS;
        }
    }

    // Store the lease
    public record Lease(
            @Nullable UUID dockId,
            @Nullable String connectorKey,
            int queuePosition,
            HoldingPlacement holdingPlacement
    ) {
        private static final Lease NONE = new Lease(
                null, null, 0, HoldingPlacement.NONE);

        // Initialize the lease
        public Lease {
            queuePosition = Math.max(0, queuePosition);
            holdingPlacement = holdingPlacement == null
                    ? HoldingPlacement.NONE : holdingPlacement;
        }

        // Initialize the lease
        public Lease(@Nullable UUID dockId, @Nullable String connectorKey, int queuePosition) {
            this(dockId, connectorKey, queuePosition, HoldingPlacement.NONE);
        }

        // Initialize the lease
        public Lease(@Nullable UUID dockId, int queuePosition) {
            this(dockId, null, queuePosition, HoldingPlacement.NONE);
        }

        // Get the generic resource key
        public @Nullable String resourceKey() {
            return connectorKey;
        }

        // Check if the dock assignment was granted
        public boolean granted() {
            return dockId != null;
        }
    }

    // Handle the dock request
    private static final class DockRequest {
        // Requester
        private final RequestKey requester;
        // Sequence
        private final long sequence;
        // Tracked candidates
        private List<DockSlot> candidates;
        // Tracked occupied docks
        private Set<DockSlot> occupiedDocks;
        // Current owned dock
        private @Nullable DockSlot ownedDock;
        // Current priority
        private RequestPriority priority;
        // Vessel envelope
        private VesselEnvelope vessel;
        // Last seen
        private long lastSeen;
        // Queue time
        private final long queuedAt;
        // Current assigned dock
        private @Nullable DockSlot assignedDock;

        // Initialize the dock request
        private DockRequest(
                RequestKey requester,
                long sequence,
                List<DockSlot> candidates,
                Set<DockSlot> occupiedDocks,
                @Nullable DockSlot ownedDock,
                RequestPriority priority,
                VesselEnvelope vessel,
                long lastSeen,
                long queuedAt
        ) {
            this.requester = requester;
            this.sequence = sequence;
            this.candidates = candidates;
            this.occupiedDocks = occupiedDocks;
            this.ownedDock = ownedDock;
            this.priority = priority;
            this.vessel = vessel;
            this.lastSeen = lastSeen;
            this.queuedAt = queuedAt;
        }
    }
}
