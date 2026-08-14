package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

// Build one stable view of every loaded body and physical connection in a Sable assembly
public final class SableAssemblyTopologyApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String EMPTY_FINGERPRINT = "0000000000000000";
    private static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);
    private static final Comparator<Edge> EDGE_ORDER = Comparator
            .comparing(Edge::firstSubLevelId, UUID_ORDER)
            .thenComparing(Edge::secondSubLevelId, UUID_ORDER)
            .thenComparing(edge -> edge.kind().name());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable assembly topology API
    private SableAssemblyTopologyApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Discover the sable assembly topology API
    public static Topology discover(@Nullable ServerLevel level, @Nullable UUID rootSubLevelId) {
        return discover(level, rootSubLevelId, ActorFilter.ALL, ActorClassifier.STRUCTURAL);
    }

    // Discover the sable assembly topology API
    public static Topology discover(@Nullable ServerLevel level, @Nullable UUID rootSubLevelId,
                                    @Nullable ActorClassifier classifier) {
        return discover(level, rootSubLevelId, ActorFilter.ALL, classifier);
    }

    // Discover the sable assembly topology API
    public static Topology discover(@Nullable ServerLevel level, @Nullable UUID rootSubLevelId,
                                    @Nullable ActorFilter filter,
                                    @Nullable ActorClassifier classifier) {
        if (level == null || rootSubLevelId == null) {
            return Topology.unavailable(rootSubLevelId);
        }
        try {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null
                    || !(container.getSubLevel(rootSubLevelId) instanceof ServerSubLevel root)) {
                return Topology.unavailable(rootSubLevelId);
            }
            return discover(root, filter, classifier);
        } catch (RuntimeException | LinkageError err) {
            return Topology.unavailable(rootSubLevelId);
        }
    }

    // Discover the sable assembly topology API
    public static Topology discover(@Nullable ServerSubLevel root) {
        return discover(root, ActorFilter.ALL, ActorClassifier.STRUCTURAL);
    }

    // Discover the sable assembly topology API
    public static Topology discover(@Nullable ServerSubLevel root,
                                    @Nullable ActorClassifier classifier) {
        return discover(root, ActorFilter.ALL, classifier);
    }

    // Discover the sable assembly topology API
    public static Topology discover(@Nullable ServerSubLevel root,
                                    @Nullable ActorFilter filter,
                                    @Nullable ActorClassifier classifier) {
        UUID rootId = id(root);
        if (!usable(root)) {
            return Topology.unavailable(rootId);
        }
        ActorFilter effectiveFilter = filter == null ? ActorFilter.ALL : filter;
        ActorClassifier effectiveClassifier = classifier == null
                ? ActorClassifier.STRUCTURAL : classifier;
        try {
            Map<UUID, ServerSubLevel> loaded = loadedBodies(root);
            loaded.put(rootId, root);

            Map<EdgeKey, SableAssemblyConnection.Kind> edgeKinds = new HashMap<>();
            for (ServerSubLevel owner : loaded.values()) {
                for (BlockEntitySubLevelActor actor : actors(owner)) {
                    if (!include(effectiveFilter, owner, actor)) {
                        continue;
                    }
                    collectSableDependencies(owner, actor, loaded, effectiveClassifier, edgeKinds);
                    collectProvidedConnections(owner, actor, loaded, edgeKinds);
                }
            }

            List<Edge> allEdges = edgeKinds.entrySet().stream()
                    .map(entry -> new Edge(entry.getKey().first(), entry.getKey().second(), entry.getValue()))
                    .sorted(EDGE_ORDER)
                    .toList();
            Map<UUID, List<Edge>> adjacency = adjacency(allEdges);
            Map<UUID, Integer> depths = breadthFirstDepths(rootId, adjacency);
            Set<UUID> reachable = depths.keySet();
            List<Edge> reachableEdges = allEdges.stream()
                    .filter(edge -> reachable.contains(edge.firstSubLevelId())
                            && reachable.contains(edge.secondSubLevelId()))
                    .toList();

            Map<UUID, Integer> carriageDepths = carriageDepths(rootId, adjacency, reachable);
            List<MutablePartition> mutablePartitions = partitions(rootId, reachable, reachableEdges,
                    depths, carriageDepths);
            Map<UUID, UUID> partitionByBody = new HashMap<>();
            List<CarriagePartition> partitions = new ArrayList<>();
            for (MutablePartition partition : mutablePartitions) {
                partition.bodyIds().forEach(id -> partitionByBody.put(id, partition.rootId()));
                partitions.add(new CarriagePartition(partition.rootId(), partition.bodyIds(),
                        partition.depth(), partition.rootId().equals(rootId)));
            }

            List<Body> bodies = reachable.stream()
                    .map(loaded::get)
                    .filter(SableAssemblyTopologyApi::usable)
                    .map(body -> new Body(body.getUniqueId(), body,
                            depths.getOrDefault(body.getUniqueId(), -1),
                            carriageDepths.getOrDefault(body.getUniqueId(), -1),
                            partitionByBody.getOrDefault(body.getUniqueId(), body.getUniqueId())))
                    .sorted(Comparator.comparingInt(Body::depth)
                            .thenComparing(Body::subLevelId, UUID_ORDER))
                    .toList();
            int maximumDepth = depths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            return new Topology(rootId, true, bodies, reachableEdges, partitions,
                    maximumDepth, fingerprint(rootId, bodies, reachableEdges));
        } catch (RuntimeException | LinkageError err) {
            return Topology.unavailable(rootId);
        }
    }

    // Get the loaded bodies
    private static Map<UUID, ServerSubLevel> loadedBodies(ServerSubLevel root) {
        Map<UUID, ServerSubLevel> loaded = new LinkedHashMap<>();
        SubLevelContainer container = SubLevelContainer.getContainer(root.getLevel());
        if (container != null) {
            for (SubLevel candidate : container.getAllSubLevels()) {
                if (candidate instanceof ServerSubLevel body && usable(body)
                        && body.getLevel() == root.getLevel()) {
                    loaded.put(body.getUniqueId(), body);
                }
            }
        }
        return loaded;
    }

    // Get the actors
    private static List<BlockEntitySubLevelActor> actors(ServerSubLevel owner) {
        List<BlockEntitySubLevelActor> actors = new ArrayList<>();
        try {
            Iterable<BlockEntitySubLevelActor> iterable = owner.getPlot().getBlockEntityActors();
            if (iterable != null) {
                iterable.forEach(actor -> {
                    if (actor != null) {
                        actors.add(actor);
                    }
                });
            }
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        return actors;
    }

    // Include the sable assembly topology API
    private static boolean include(ActorFilter filter, ServerSubLevel owner,
                                   BlockEntitySubLevelActor actor) {
        try {
            return filter.include(owner, actor);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Collect the sable dependencies
    private static void collectSableDependencies(
            ServerSubLevel owner, BlockEntitySubLevelActor actor,
            Map<UUID, ServerSubLevel> loaded, ActorClassifier classifier,
            Map<EdgeKey, SableAssemblyConnection.Kind> edges
    ) {
        List<ServerSubLevel> dependencies = new ArrayList<>();
        try {
            Iterable<SubLevel> values = actor.sable$getConnectionDependencies();
            if (values != null) {
                for (SubLevel val : values) {
                    if (val instanceof ServerSubLevel target && usable(target)
                            && loaded.containsKey(target.getUniqueId())) {
                        dependencies.add(target);
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            return;
        }
        dependencies.sort(Comparator.comparing(ServerSubLevel::getUniqueId, UUID_ORDER));
        for (ServerSubLevel target : dependencies) {
            SableAssemblyConnection.Kind kind;
            try {
                kind = classifier.classify(owner, actor, target);
            } catch (RuntimeException | LinkageError ignored) {
                continue;
            }
            merge(edges, owner.getUniqueId(), target.getUniqueId(), kind);
        }
    }

    // Collect the provided connections
    private static void collectProvidedConnections(
            ServerSubLevel owner, BlockEntitySubLevelActor actor,
            Map<UUID, ServerSubLevel> loaded,
            Map<EdgeKey, SableAssemblyConnection.Kind> edges
    ) {
        if (!(actor instanceof SableAssemblyConnectionProvider provider)) {
            return;
        }
        List<SableAssemblyConnection> connections = new ArrayList<>();
        try {
            Iterable<SableAssemblyConnection> values = provider.sableAssemblyConnections(owner);
            if (values != null) {
                values.forEach(connection -> {
                    if (connection != null) {
                        connections.add(connection);
                    }
                });
            }
        } catch (RuntimeException | LinkageError ignored) {
            return;
        }
        connections.sort(Comparator
                .comparing(SableAssemblyConnection::targetSubLevelId,
                        Comparator.nullsLast(UUID_ORDER))
                .thenComparing(connection -> connection.kind().name()));
        for (SableAssemblyConnection connection : connections) {
            UUID targetId = connection.targetSubLevelId();
            if (targetId != null && loaded.containsKey(targetId)) {
                merge(edges, owner.getUniqueId(), targetId, connection.kind());
            }
        }
    }

    // Merge the sable assembly topology API
    private static void merge(Map<EdgeKey, SableAssemblyConnection.Kind> edges,
                              UUID first, UUID second,
                              @Nullable SableAssemblyConnection.Kind kind) {
        if (first == null || second == null || first.equals(second) || kind == null) {
            return;
        }
        EdgeKey key = EdgeKey.of(first, second);
        edges.merge(key, kind, (existing, replacement) ->
                existing == SableAssemblyConnection.Kind.CARRIAGE_COUPLER
                        || replacement == SableAssemblyConnection.Kind.CARRIAGE_COUPLER
                        ? SableAssemblyConnection.Kind.CARRIAGE_COUPLER
                        : SableAssemblyConnection.Kind.STRUCTURAL);
    }

    // Get the adjacency
    private static Map<UUID, List<Edge>> adjacency(Collection<Edge> edges) {
        Map<UUID, List<Edge>> adjacency = new HashMap<>();
        for (Edge edge : edges) {
            adjacency.computeIfAbsent(edge.firstSubLevelId(), ignored -> new ArrayList<>()).add(edge);
            adjacency.computeIfAbsent(edge.secondSubLevelId(), ignored -> new ArrayList<>()).add(edge);
        }
        adjacency.values().forEach(values -> values.sort(EDGE_ORDER));
        return adjacency;
    }

    // Get the breadth first depths
    private static Map<UUID, Integer> breadthFirstDepths(UUID rootId,
                                                         Map<UUID, List<Edge>> adjacency) {
        Map<UUID, Integer> depths = new LinkedHashMap<>();
        Deque<UUID> pending = new ArrayDeque<>();
        depths.put(rootId, 0);
        pending.add(rootId);
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            int nextDepth = depths.get(current) + 1;
            for (Edge edge : adjacency.getOrDefault(current, List.of())) {
                UUID next = edge.other(current);
                if (next != null && !depths.containsKey(next)) {
                    depths.put(next, nextDepth);
                    pending.addLast(next);
                }
            }
        }
        return depths;
    }

    // Get the carriage depths
    private static Map<UUID, Integer> carriageDepths(UUID rootId,
                                                     Map<UUID, List<Edge>> adjacency,
                                                     Set<UUID> reachable) {
        Map<UUID, Integer> depths = new HashMap<>();
        Deque<UUID> pending = new ArrayDeque<>();
        depths.put(rootId, 0);
        pending.add(rootId);
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            int currentDepth = depths.get(current);
            for (Edge edge : adjacency.getOrDefault(current, List.of())) {
                UUID next = edge.other(current);
                if (next == null || !reachable.contains(next)) {
                    continue;
                }
                int candidate = currentDepth
                        + (edge.kind() == SableAssemblyConnection.Kind.CARRIAGE_COUPLER ? 1 : 0);
                Integer prev = depths.get(next);
                if (prev == null || candidate < prev) {
                    depths.put(next, candidate);
                    if (candidate == currentDepth) {
                        pending.addFirst(next);
                    } else {
                        pending.addLast(next);
                    }
                }
            }
        }
        return depths;
    }

    // Get the partitions
    private static List<MutablePartition> partitions(
            UUID rootId, Set<UUID> reachable, List<Edge> edges,
            Map<UUID, Integer> depths, Map<UUID, Integer> carriageDepths
    ) {
        Map<UUID, List<UUID>> structural = new HashMap<>();
        for (Edge edge : edges) {
            if (edge.kind() == SableAssemblyConnection.Kind.STRUCTURAL) {
                structural.computeIfAbsent(edge.firstSubLevelId(), ignored -> new ArrayList<>())
                        .add(edge.secondSubLevelId());
                structural.computeIfAbsent(edge.secondSubLevelId(), ignored -> new ArrayList<>())
                        .add(edge.firstSubLevelId());
            }
        }
        structural.values().forEach(values -> values.sort(UUID_ORDER));

        Set<UUID> unassigned = new LinkedHashSet<>();
        reachable.stream().sorted(Comparator
                .comparingInt((UUID id) -> depths.getOrDefault(id, Integer.MAX_VALUE))
                .thenComparing(UUID_ORDER)).forEach(unassigned::add);
        List<MutablePartition> res = new ArrayList<>();
        while (!unassigned.isEmpty()) {
            UUID seed = unassigned.iterator().next();
            Set<UUID> component = new HashSet<>();
            Deque<UUID> pending = new ArrayDeque<>();
            pending.add(seed);
            while (!pending.isEmpty()) {
                UUID current = pending.removeFirst();
                if (!component.add(current)) {
                    continue;
                }
                for (UUID next : structural.getOrDefault(current, List.of())) {
                    if (reachable.contains(next) && !component.contains(next)) {
                        pending.addLast(next);
                    }
                }
            }
            unassigned.removeAll(component);
            UUID partitionRoot = component.stream().min(Comparator
                    .comparingInt((UUID id) -> depths.getOrDefault(id, Integer.MAX_VALUE))
                    .thenComparing(UUID_ORDER)).orElse(seed);
            List<UUID> ids = component.stream().sorted(Comparator
                    .comparingInt((UUID id) -> depths.getOrDefault(id, Integer.MAX_VALUE))
                    .thenComparing(UUID_ORDER)).toList();
            int carriageDepth = component.stream()
                    .mapToInt(id -> carriageDepths.getOrDefault(id, Integer.MAX_VALUE))
                    .min().orElse(-1);
            res.add(new MutablePartition(partitionRoot, ids,
                    carriageDepth == Integer.MAX_VALUE ? -1 : carriageDepth));
        }
        res.sort(Comparator
                .comparing((MutablePartition partition) -> !partition.bodyIds().contains(rootId))
                .thenComparingInt(MutablePartition::depth)
                .thenComparing(MutablePartition::rootId, UUID_ORDER));
        return res;
    }

    // Get the fingerprint
    private static String fingerprint(UUID rootId, List<Body> bodies, List<Edge> edges) {
        long hash = 0xcbf29ce484222325L;
        hash = hash(hash, rootId.toString());
        for (Body body : bodies.stream().sorted(Comparator.comparing(Body::subLevelId, UUID_ORDER)).toList()) {
            hash = hash(hash, "|b:" + body.subLevelId());
        }
        for (Edge edge : edges) {
            hash = hash(hash, "|e:" + edge.firstSubLevelId() + ':'
                    + edge.secondSubLevelId() + ':' + edge.kind().name());
        }
        return String.format(java.util.Locale.ROOT, "%016x", hash);
    }

    // Get the hash
    private static long hash(long val, String text) {
        long res = val;
        for (byte character : text.getBytes(StandardCharsets.UTF_8)) {
            res ^= character & 0xffL;
            res *= 0x100000001b3L;
        }
        return res;
    }

    // Check if the sublevel can be used
    private static boolean usable(@Nullable ServerSubLevel body) {
        try {
            return body != null && body.getUniqueId() != null && !body.isRemoved()
                    && body.getLevel() != null && body.getPlot() != null;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Get the id
    private static @Nullable UUID id(@Nullable ServerSubLevel body) {
        try {
            return body == null ? null : body.getUniqueId();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    // Expose the actor filter
    @FunctionalInterface
    public interface ActorFilter {
        ActorFilter ALL = (owner, actor) -> true;

        // Include the actor filter
        boolean include(ServerSubLevel owner, BlockEntitySubLevelActor actor);
    }

    // Expose the actor classifier
    @FunctionalInterface
    public interface ActorClassifier {
        ActorClassifier STRUCTURAL = (owner, actor, target) ->
                SableAssemblyConnection.Kind.STRUCTURAL;

        // Returning null excludes this dependency
        @Nullable SableAssemblyConnection.Kind classify(ServerSubLevel owner,
                                                        BlockEntitySubLevelActor actor,
                                                        ServerSubLevel target);
    }

    // Store the body
    public record Body(UUID subLevelId, ServerSubLevel subLevel, int depth,
                       int carriageDepth, UUID carriageRootSubLevelId) {
        // Initialize the body
        public Body {
            depth = Math.max(-1, depth);
            carriageDepth = Math.max(-1, carriageDepth);
            carriageRootSubLevelId = carriageRootSubLevelId == null
                    ? subLevelId : carriageRootSubLevelId;
        }
    }

    // Store the edge
    public record Edge(UUID firstSubLevelId, UUID secondSubLevelId,
                       SableAssemblyConnection.Kind kind) {
        // Initialize the edge
        public Edge {
            kind = kind == null ? SableAssemblyConnection.Kind.STRUCTURAL : kind;
            if (firstSubLevelId != null && secondSubLevelId != null
                    && UUID_ORDER.compare(firstSubLevelId, secondSubLevelId) > 0) {
                UUID swap = firstSubLevelId;
                firstSubLevelId = secondSubLevelId;
                secondSubLevelId = swap;
            }
        }

        // Get the other
        public @Nullable UUID other(@Nullable UUID subLevelId) {
            if (subLevelId == null) {
                return null;
            }
            if (subLevelId.equals(firstSubLevelId)) {
                return secondSubLevelId;
            }
            return subLevelId.equals(secondSubLevelId) ? firstSubLevelId : null;
        }
    }

    // Store the carriage partition
    public record CarriagePartition(UUID rootSubLevelId, List<UUID> bodyIds,
                                    int depth, boolean primary) {
        // Initialize the carriage partition
        public CarriagePartition {
            bodyIds = bodyIds == null ? List.of() : List.copyOf(bodyIds);
            depth = Math.max(-1, depth);
        }

        // Check if this contains the value
        public boolean contains(@Nullable UUID subLevelId) {
            return subLevelId != null && bodyIds.contains(subLevelId);
        }
    }

    // Store the topology
    public record Topology(@Nullable UUID rootSubLevelId, boolean available,
                           List<Body> bodies, List<Edge> edges,
                           List<CarriagePartition> carriagePartitions,
                           int maximumDepth, String fingerprint) {
        // Initialize the topology
        public Topology {
            bodies = bodies == null ? List.of() : List.copyOf(bodies);
            edges = edges == null ? List.of() : List.copyOf(edges);
            carriagePartitions = carriagePartitions == null
                    ? List.of() : List.copyOf(carriagePartitions);
            maximumDepth = Math.max(0, maximumDepth);
            fingerprint = fingerprint == null || fingerprint.isBlank()
                    ? EMPTY_FINGERPRINT : fingerprint;
        }

        // Get the loaded bodies
        public List<ServerSubLevel> loadedBodies() {
            return bodies.stream().map(Body::subLevel).toList();
        }

        // Get the loaded body ids
        public Set<UUID> loadedBodyIds() {
            LinkedHashSet<UUID> ids = new LinkedHashSet<>();
            bodies.stream().map(Body::subLevelId).filter(java.util.Objects::nonNull)
                    .forEach(ids::add);
            return Collections.unmodifiableSet(ids);
        }

        // Get the body
        public Optional<Body> body(@Nullable UUID subLevelId) {
            return subLevelId == null ? Optional.empty() : bodies.stream()
                    .filter(body -> subLevelId.equals(body.subLevelId())).findFirst();
        }

        // Get the depth
        public OptionalInt depth(@Nullable UUID subLevelId) {
            Optional<Body> body = body(subLevelId);
            return body.isPresent() ? OptionalInt.of(body.get().depth()) : OptionalInt.empty();
        }

        // Get the carriage
        public Optional<CarriagePartition> carriage(@Nullable UUID subLevelId) {
            return subLevelId == null ? Optional.empty() : carriagePartitions.stream()
                    .filter(partition -> partition.contains(subLevelId)).findFirst();
        }

        // Create an unavailable topology
        private static Topology unavailable(@Nullable UUID rootSubLevelId) {
            return new Topology(rootSubLevelId, false, List.of(), List.of(), List.of(),
                    0, EMPTY_FINGERPRINT);
        }
    }

    // Store the edge key
    private record EdgeKey(UUID first, UUID second) {
        // Create the edge key
        private static EdgeKey of(UUID first, UUID second) {
            return UUID_ORDER.compare(first, second) <= 0
                    ? new EdgeKey(first, second) : new EdgeKey(second, first);
        }
    }

    // Store the mutable partition
    private record MutablePartition(UUID rootId, List<UUID> bodyIds, int depth) {
    }
}
