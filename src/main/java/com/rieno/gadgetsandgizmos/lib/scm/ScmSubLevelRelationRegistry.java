package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

// Register optional physical links which make one loaded sub-level a child of another
public final class ScmSubLevelRelationRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<Level, LoadedRelations> LOADED_RELATIONS = new WeakHashMap<>();
    private static volatile List<Entry> orderedProviders = List.of();
    private static long providerRevision;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SCM sub-level relation registry
    private ScmSubLevelRelationRegistry() {
    }

    // Register one optional sub-level relation provider
    public static synchronized void register(ResourceLocation id, int priority, Provider provider) {
        register(id, priority, null, provider);
    }

    // Limit an optional provider to the block entities it can adapt
    public static synchronized void register(ResourceLocation id, int priority,
            Predicate<BlockEntity> filter, Provider provider){
        Objects.requireNonNull(id, "id");
        if (ENTRIES.containsKey(id)) {
            throw new IllegalStateException("SCM sub-level relation provider already registered: " + id);
        }
        ENTRIES.put(id, new Entry(priority, filter, Objects.requireNonNull(provider, "provider")));
        refreshProviders();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remove one optional sub-level relation provider
    public static synchronized void unregister(ResourceLocation id) {
        if(ENTRIES.remove(id) != null) refreshProviders();
    }

    // Get the provider revision for consumers that cache derived topology
    public static synchronized long revision(){
        return providerRevision;
    }

    // Share loaded relation discovery across controllers and collision queries in one tick
    public static List<Relation> loadedRelations(SubLevel root){
        List<Entry> providers = orderedProviders;
        if(root == null || root.isRemoved() || providers.isEmpty()) return List.of();
        Level level = root.getLevel();
        if(level == null) return List.of();
        long tick = level.getGameTime();
        long topologyRevision = SableAssemblyTopologyInvalidation.revision(SableLevelApi.serverLevel(level));
        long registryRevision;
        synchronized(ScmSubLevelRelationRegistry.class){
            registryRevision = providerRevision;
            LoadedRelations cached = LOADED_RELATIONS.get(level);
            if(cached != null && cached.tick() == tick
                    && cached.topologyRevision() == topologyRevision
                    && cached.providerRevision() == registryRevision
                    && cached.bodyIds().contains(root.getUniqueId())) return cached.relations();
        }
        Set<UUID> bodyIds = new LinkedHashSet<>();
        List<ScopedBlockEntity> scoped = new ArrayList<>();
        for(Object candidate : SubLevelBlockEntityCollector.getSubLevels(level)){
            if(!(candidate instanceof SubLevel body) || body.isRemoved()) continue;
            collectLoaded(body, bodyIds, scoped, providers);
        }
        collectLoaded(root, bodyIds, scoped, providers);
        List<Relation> relations = relations(level, scoped);
        synchronized(ScmSubLevelRelationRegistry.class){
            if(providerRevision == registryRevision){
                LOADED_RELATIONS.put(level, new LoadedRelations(tick, topologyRevision,
                        registryRevision, Set.copyOf(bodyIds), relations));
            }
        }
        return relations;
    }

    // Release a dimension snapshot when its level unloads
    public static synchronized void forgetLoadedRelations(Level level){
        LOADED_RELATIONS.remove(level);
    }

    // Keep provider order stable until registration changes
    private static void refreshProviders(){
        orderedProviders = ENTRIES.values().stream()
                .sorted(Comparator.comparingInt(Entry::priority).reversed()).toList();
        providerRevision++;
        LOADED_RELATIONS.clear();
    }

    // Read each loaded body once even when the root also appears in the container
    private static void collectLoaded(SubLevel body, Set<UUID> bodyIds,
            List<ScopedBlockEntity> scoped, List<Entry> providers){
        if(!bodyIds.add(body.getUniqueId())) return;
        for(BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(body)){
            if(!matchesProvider(blockEntity, providers)) continue;
            scoped.add(new ScopedBlockEntity(body.getUniqueId(), blockEntity));
        }
    }

    // Avoid retaining scoped entries for blocks no loaded provider can use
    private static boolean matchesProvider(BlockEntity blockEntity, List<Entry> providers){
        for(Entry entry : providers){
            if(entry.filter() == null) return true;
            try{
                if(entry.filter().test(blockEntity)) return true;
            }catch(RuntimeException | LinkageError ignored){
                return true;
            }
        }
        return false;
    }

    // Collect the relations exposed by every registered provider
    public static List<Relation> relations(
            Level level, Collection<ScopedBlockEntity> blockEntities
    ) {
        if (level == null || blockEntities == null || blockEntities.isEmpty()) {
            return List.of();
        }
        List<Entry> providers = orderedProviders;
        if(providers.isEmpty()) return List.of();
        Context ctx = new Context(level, List.copyOf(blockEntities));
        Set<Relation> found = new LinkedHashSet<>();
        for (Entry entry : providers) {
            Collection<Relation> provided;
            try {
                Context selected = ctx;
                if(entry.filter() != null){
                    List<ScopedBlockEntity> matching = new ArrayList<>();
                    for(ScopedBlockEntity scoped : ctx.blockEntities()){
                        if(entry.filter().test(scoped.blockEntity())) matching.add(scoped);
                    }
                    if(matching.isEmpty()) continue;
                    selected = new Context(level, matching);
                }
                provided = entry.provider().relations(selected);
            } catch (RuntimeException | LinkageError ignored) {
                continue;
            }
            if (provided == null) continue;
            provided.stream().filter(Objects::nonNull).filter(Relation::valid).forEach(found::add);
        }
        return found.stream().sorted(Comparator
                .comparing((Relation relation) -> relation.parentSubLevelId().toString())
                .thenComparing(relation -> relation.childSubLevelId().toString())
                .thenComparing(Relation::adapterId)
                .thenComparing(relation -> relation.sourceBlockPosition() == null
                        ? Long.MIN_VALUE : relation.sourceBlockPosition().asLong())
                .thenComparing(relation -> relation.sourceSubLevelId() == null
                        ? "" : relation.sourceSubLevelId().toString())).toList();
    }

    // Resolve every descendant of the requested roots without depending on a physics implementation
    public static Set<UUID> descendants(
            Collection<UUID> roots, Collection<Relation> relations
    ) {
        Map<UUID, List<UUID>> children = new LinkedHashMap<>();
        if (relations != null) {
            for (Relation relation : relations) {
                if (relation == null || !relation.valid()) continue;
                children.computeIfAbsent(relation.parentSubLevelId(), ignored -> new ArrayList<>())
                        .add(relation.childSubLevelId());
            }
        }
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        if (roots != null) {
            roots.stream().filter(Objects::nonNull).forEach(pending::addLast);
        }
        Set<UUID> resolved = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            if (!resolved.add(current)) continue;
            children.getOrDefault(current, List.of()).stream()
                    .filter(Objects::nonNull)
                    .filter(child -> !resolved.contains(child))
                    .forEach(pending::addLast);
        }
        return Set.copyOf(resolved);
    }

    // Resolve every body physically linked to the requested roots without assuming a parent side
    public static Set<UUID> connected(
            Collection<UUID> roots, Collection<Relation> relations
    ) {
        Map<UUID, List<UUID>> links = new LinkedHashMap<>();
        if (relations != null) {
            for (Relation relation : relations) {
                if (relation == null || !relation.valid()) continue;
                links.computeIfAbsent(relation.parentSubLevelId(), ignored -> new ArrayList<>())
                        .add(relation.childSubLevelId());
                links.computeIfAbsent(relation.childSubLevelId(), ignored -> new ArrayList<>())
                        .add(relation.parentSubLevelId());
            }
        }
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        if (roots != null) {
            roots.stream().filter(Objects::nonNull).forEach(pending::addLast);
        }
        Set<UUID> resolved = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            if (!resolved.add(current)) continue;
            links.getOrDefault(current, List.of()).stream()
                    .filter(Objects::nonNull)
                    .filter(link -> !resolved.contains(link))
                    .forEach(pending::addLast);
        }
        return Set.copyOf(resolved);
    }

    // Order selected links from the root, rejecting branches, gaps and loops
    public static List<Relation> orderedChain(UUID root, Collection<Relation> relations){
        if(root == null || relations == null) return List.of();
        Set<Relation> remaining = new LinkedHashSet<>(relations);
        if(remaining.stream().anyMatch(relation -> relation == null || !relation.valid())) return List.of();
        List<Relation> ordered = new ArrayList<>();
        Set<UUID> visited = new LinkedHashSet<>();
        UUID body = root;
        visited.add(body);
        while(!remaining.isEmpty()){
            Relation next = null;
            for(Relation relation : remaining){
                if(!body.equals(relation.parentSubLevelId()) && !body.equals(relation.childSubLevelId())) continue;
                if(next != null) return List.of();
                next = relation;
            }
            if(next == null) return List.of();
            body = body.equals(next.parentSubLevelId()) ? next.childSubLevelId() : next.parentSubLevelId();
            if(!visited.add(body)) return List.of();
            ordered.add(next);
            remaining.remove(next);
        }
        return List.copyOf(ordered);
    }

    // Store one block entity together with its owning sub-level id
    public record ScopedBlockEntity(UUID subLevelId, BlockEntity blockEntity) {
        // Check whether this points at a loaded block entity
        public boolean valid() {
            return subLevelId != null && blockEntity != null && !blockEntity.isRemoved();
        }
    }

    // Store one directed parent-to-child physical relation
    public record Relation(UUID parentSubLevelId, UUID childSubLevelId, String adapterId,
                           BlockPos sourceBlockPosition, UUID sourceSubLevelId) {
        // Normalize the adapter id
        public Relation {
            adapterId = adapterId == null ? "" : adapterId;
            sourceBlockPosition = sourceBlockPosition == null
                    ? null : sourceBlockPosition.immutable();
        }

        // Initialize one relation with the sub-level which owns its attachment block
        public Relation(UUID parentSubLevelId, UUID childSubLevelId, String adapterId,
                        UUID sourceSubLevelId, BlockPos sourceBlockPosition) {
            this(parentSubLevelId, childSubLevelId, adapterId, sourceBlockPosition, sourceSubLevelId);
        }

        // Initialize one relation with an attachment block and no owner identity
        public Relation(UUID parentSubLevelId, UUID childSubLevelId, String adapterId,
                        BlockPos sourceBlockPosition) {
            this(parentSubLevelId, childSubLevelId, adapterId, sourceBlockPosition, null);
        }

        // Initialize one relation without an attachment block
        public Relation(UUID parentSubLevelId, UUID childSubLevelId, String adapterId) {
            this(parentSubLevelId, childSubLevelId, adapterId, (BlockPos) null, null);
        }

        // Check whether this relates two distinct loaded sub-level identities
        public boolean valid() {
            return parentSubLevelId != null && childSubLevelId != null
                    && !parentSubLevelId.equals(childSubLevelId);
        }

        // Check whether this relation identifies its attachment block
        public boolean hasSourceBlockPosition() {
            return sourceBlockPosition != null;
        }

        // Check whether one live actuator is the attachment which created this relation
        public boolean matchesSource(UUID subLevelId, BlockPos blockPosition) {
            if (!hasSourceBlockPosition() || blockPosition == null
                    || !sourceBlockPosition.equals(blockPosition)) {
                return false;
            }
            return sourceSubLevelId == null || sourceSubLevelId.equals(subLevelId);
        }
    }

    // Pass all loaded block entities to one relation provider
    public record Context(Level level, List<ScopedBlockEntity> blockEntities) {
        // Normalize the source list before optional compatibility code reads it
        public Context {
            blockEntities = blockEntities == null ? List.of() : blockEntities.stream()
                    .filter(Objects::nonNull).filter(ScopedBlockEntity::valid).toList();
        }
    }

    // Expose one optional relation provider
    @FunctionalInterface
    public interface Provider {
        // Get the current parent-to-child relations
        Collection<Relation> relations(Context ctx);
    }

    // Store one provider priority and callback
    private record Entry(int priority, Predicate<BlockEntity> filter, Provider provider) {
    }

    // Retain only identities and resolved links so a snapshot cannot keep a level alive
    private record LoadedRelations(long tick, long topologyRevision, long providerRevision,
            Set<UUID> bodyIds, List<Relation> relations){}
}
