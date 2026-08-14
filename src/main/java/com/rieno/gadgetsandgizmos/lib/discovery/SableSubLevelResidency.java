package com.rieno.gadgetsandgizmos.lib.discovery;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.Codec;
import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
            return retain
                    ? container.addForceLoadTicket(subLevel, ticket, owner)
                    : container.removeForceLoadTicket(subLevel, ticket, owner);
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

    // Manage a Sable sublevel residency lease
    public static final class Lease implements AutoCloseable {
        // Owner
        private final String owner;
        // Tracked retained
        private final Map<UUID, ServerSubLevel> retained = new LinkedHashMap<>();
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

        // Detach the lease
        public void detach() {
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
    }
}
