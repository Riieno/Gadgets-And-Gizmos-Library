package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

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

// Register optional physical links which make one loaded sub-level a child of another
public final class ScmSubLevelRelationRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();

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
        Objects.requireNonNull(id, "id");
        if (ENTRIES.containsKey(id)) {
            throw new IllegalStateException("SCM sub-level relation provider already registered: " + id);
        }
        ENTRIES.put(id, new Entry(priority, Objects.requireNonNull(provider, "provider")));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remove one optional sub-level relation provider
    public static synchronized void unregister(ResourceLocation id) {
        ENTRIES.remove(id);
    }

    // Collect the relations exposed by every registered provider
    public static List<Relation> relations(
            Level level, Collection<ScopedBlockEntity> blockEntities
    ) {
        if (level == null || blockEntities == null || blockEntities.isEmpty()) {
            return List.of();
        }
        Context ctx = new Context(level, List.copyOf(blockEntities));
        List<Entry> providers;
        synchronized (ScmSubLevelRelationRegistry.class) {
            providers = ENTRIES.values().stream()
                    .sorted(Comparator.comparingInt(Entry::priority).reversed())
                    .toList();
        }
        Set<Relation> found = new LinkedHashSet<>();
        for (Entry entry : providers) {
            Collection<Relation> provided;
            try {
                provided = entry.provider().relations(ctx);
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
    private record Entry(int priority, Provider provider) {
    }
}
