package com.rieno.gadgetsandgizmos.lib.discovery;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.Codec;
import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Keep the chunks needed by an active Sable controller session resident
public final class SableSubLevelResidency {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation TICKET_ID = ResourceLocation.fromNamespaceAndPath(
            GadgetsNGizmosLibrary.MOD_ID, "sublevel_residency");
    private static final ResourceLocation LEGACY_TICKET_ID = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "sublevel_residency");
    private static final SubLevelLoadingTicketType<String> RESIDENCY_TICKET =
            SubLevelLoadingTicketType.create(TICKET_ID, Codec.STRING);
    private static final SubLevelLoadingTicketType<String> LEGACY_RESIDENCY_TICKET =
            SubLevelLoadingTicketType.create(LEGACY_TICKET_ID, Codec.STRING);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable sub level residency
    private SableSubLevelResidency() {
    }

    // Load the Sable residency ticket type
    public static void bootstrap() {
        // Load the ticket type before Sable restores saved leases
        RESIDENCY_TICKET.name();
        LEGACY_RESIDENCY_TICKET.name();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the ticket
    private static boolean invokeTicket(ServerSubLevel subLevel, SubLevelLoadingTicketType<String> ticket,
                                        String owner, boolean retain) {
        try {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
            if (container == null) {
                return false;
            }
            if(retain){
                // A restored ticket already belongs to this owner even when Sable reports no change
                container.addForceLoadTicket(subLevel, ticket, owner);
                return true;
            }
            return container.removeForceLoadTicket(subLevel, ticket, owner);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Get the lease
    public static Lease lease(String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("A Sable residency lease requires an owner");
        }
        return new Lease(owner);
    }

    // Release this owner's restored tickets even before a new lease has adopted them
    public static void release(String owner, Collection<? extends SubLevel> subLevels){
        if(owner == null || owner.isBlank() || subLevels == null) return;
        for(SubLevel subLevel : subLevels){
            if(!(subLevel instanceof ServerSubLevel serverSubLevel)) continue;
            invokeTicket(serverSubLevel, RESIDENCY_TICKET, owner, false);
            invokeTicket(serverSubLevel, LEGACY_RESIDENCY_TICKET, owner, false);
        }
    }

    // Check restored plot chunks without waiting for unused reserved chunk slots
    public static boolean isFullyLoaded(SubLevel subLevel) {
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            return false;
        }
        try {
            LevelPlot plot = serverSubLevel.getPlot();
            if (plot == null) {
                return false;
            }
            var chunks = plot.getLoadedChunks();
            if(chunks == null || chunks.isEmpty()) return false;
            for(PlotChunkHolder holder : chunks){
                if(holder == null || holder.getChunk() == null) return false;
            }
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Check whether every retained sublevel is fully available.
    public static boolean areFullyLoaded(Collection<? extends SubLevel> subLevels) {
        if (subLevels == null || subLevels.isEmpty()) {
            return false;
        }
        for (SubLevel subLevel : subLevels) {
            if (!isFullyLoaded(subLevel)) {
                return false;
            }
        }
        return true;
    }

    // Manage a Sable sublevel residency lease
    public static final class Lease implements AutoCloseable {
        // Owner
        private final String owner;
        // Tracked retained
        private final Map<UUID, ServerSubLevel> retained = new LinkedHashMap<>();
        private final Map<ServerLevel, MovingChunkResidency.Lease> terrain = new LinkedHashMap<>();
        private final Map<ServerLevel, Long> terrainTicks = new LinkedHashMap<>();
        // Tracks whether lease is closed
        private boolean closed;

        // Initialize the lease
        private Lease(String owner) {
            this.owner = owner;
        }

        // Get the owner
        public String owner() {
            return owner;
        }

        // Get the retained sublevel ids
        public Set<UUID> retainedSubLevelIds() {
            return Set.copyOf(retained.keySet());
        }

        // Retain the lease
        public void retain(SubLevel subLevel) {
            if (closed || !(subLevel instanceof ServerSubLevel serverSubLevel)
                    || serverSubLevel.isRemoved()) {
                return;
            }
            ServerSubLevel prev = retained.get(serverSubLevel.getUniqueId());
            if (prev == serverSubLevel) {
                return;
            }
            if (prev != null) {
                release(prev);
            }
            // Remove the old Create Thrusters ticket before retaining the renamed library ticket
            invokeTicket(serverSubLevel, LEGACY_RESIDENCY_TICKET, owner, false);
            if (invokeTicket(serverSubLevel, RESIDENCY_TICKET, owner, true)) {
                retained.put(serverSubLevel.getUniqueId(), serverSubLevel);
            }
        }

        // Sync the lease
        public void synchronize(Collection<? extends SubLevel> subLevels) {
            if (closed) {
                return;
            }
            Map<UUID, SubLevel> desired = new LinkedHashMap<>();
            if (subLevels != null) {
                for (SubLevel subLevel : subLevels) {
                    if (subLevel instanceof ServerSubLevel && !subLevel.isRemoved()) {
                        desired.put(subLevel.getUniqueId(), subLevel);
                    }
                }
            }
            for (UUID retainedId : new LinkedHashSet<>(retained.keySet())) {
                if (!desired.containsKey(retainedId)) {
                    release(retained.remove(retainedId));
                }
            }
            desired.values().forEach(this::retain);
        }

        // Prepare horizontal terrain while keeping ship residency on Sable's ticket API
        public void prefetchMovingChunks(){
            if(closed) return;
            Map<ServerLevel, List<MovingChunkResidency.Region>> regions = new LinkedHashMap<>();
            for(ServerSubLevel body : retained.values()){
                if(body.isRemoved()) continue;
                ServerLevel level = body.getLevel();
                if(level == null || Long.valueOf(level.getGameTime()).equals(terrainTicks.get(level))) continue;
                List<MovingChunkResidency.Region> levelRegions = regions.computeIfAbsent(level, key -> new ArrayList<>());
                try{
                    RigidBodyHandle handle = RigidBodyHandle.of(body);
                    if(handle == null || !handle.isValid()) continue;
                    Vector3d velocity = handle.getLinearVelocity(new Vector3d());
                    if(Math.abs(velocity.x) <= 0.05D && Math.abs(velocity.z) <= 0.05D) continue;
                    var bounds = body.boundingBox();
                    if(bounds == null) continue;
                    levelRegions.add(new MovingChunkResidency.Region(
                            new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                            new Vec3(velocity.x, velocity.y, velocity.z)));
                }catch(RuntimeException | LinkageError ignored){
                    // Sable retains its normal terrain loading while a physics handle is unavailable
                }
            }
            for(var entry : regions.entrySet()){
                terrain.computeIfAbsent(entry.getKey(), MovingChunkResidency::lease).synchronize(entry.getValue());
                terrainTicks.put(entry.getKey(), entry.getKey().getGameTime());
            }
            for(ServerLevel level : new LinkedHashSet<>(terrain.keySet())){
                if(regions.containsKey(level) || Long.valueOf(level.getGameTime()).equals(terrainTicks.get(level))) continue;
                terrain.remove(level).close();
                terrainTicks.remove(level);
            }
        }

        // Detach the lease
        public void detach() {
            releaseTerrain();
            retained.clear();
            closed = true;
        }

        // Close the lease
        @Override
        public void close() {
            if (closed) {
                return;
            }
            for (ServerSubLevel subLevel : new LinkedHashSet<>(retained.values())) {
                release(subLevel);
            }
            releaseTerrain();
            retained.clear();
            closed = true;
        }

        // Release the lease
        private void release(ServerSubLevel subLevel) {
            if (subLevel == null) {
                return;
            }
            invokeTicket(subLevel, RESIDENCY_TICKET, owner, false);
            invokeTicket(subLevel, LEGACY_RESIDENCY_TICKET, owner, false);
        }

        // Terrain prediction is temporary even when ship tickets survive a server restart
        private void releaseTerrain(){
            terrain.values().forEach(MovingChunkResidency.Lease::close);
            terrain.clear();
            terrainTicks.clear();
        }
    }
}
