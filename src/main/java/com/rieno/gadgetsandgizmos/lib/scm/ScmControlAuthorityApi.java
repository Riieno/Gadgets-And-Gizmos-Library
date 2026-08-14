package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

// Keep one short-lived SCM control owner for each server-side ship assembly
public final class ScmControlAuthorityApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final long DEFAULT_LEASE_TICKS = 2L;
    private static final Map<MinecraftServer, Map<String, Authority>> AUTHORITIES =
            new WeakHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SCM control authority API
    private ScmControlAuthorityApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the claim
    public static ClaimResult claim(@Nullable MinecraftServer server,
                                    @Nullable String assemblyKey,
                                    @Nullable UUID controllerId,
                                    int priority,
                                    long currentTick) {
        return claim(server, assemblyKey, controllerId, priority,
                currentTick, DEFAULT_LEASE_TICKS);
    }

    // Get the claim
    public static ClaimResult claim(@Nullable MinecraftServer server,
                                    @Nullable UUID assemblyKey,
                                    @Nullable UUID controllerId,
                                    int priority,
                                    long currentTick) {
        return claim(server, key(assemblyKey), controllerId, priority,
                currentTick, DEFAULT_LEASE_TICKS);
    }

    // Add this tick's candidate and return the previous owner
    public static synchronized ClaimResult claim(@Nullable MinecraftServer server,
                                                 @Nullable String assemblyKey,
                                                 @Nullable UUID controllerId,
                                                 int priority,
                                                 long currentTick,
                                                 long leaseTicks) {
        String key = key(assemblyKey);
        if (server == null || key == null || controllerId == null || leaseTicks <= 0L) {
            return ClaimResult.denied(null);
        }
        Map<String, Authority> serverAuthorities = AUTHORITIES.computeIfAbsent(server,
                ignored -> new HashMap<>());
        Authority authority = serverAuthorities.computeIfAbsent(key, ignored -> new Authority());
        if (!authority.advance(currentTick)) {
            return ClaimResult.denied(authority.liveOwner(currentTick));
        }

        authority.candidates.put(controllerId, new Candidate(controllerId, priority,
                expiration(currentTick, leaseTicks)));
        Owner owner = authority.liveOwner(currentTick);
        boolean granted = owner != null && controllerId.equals(owner.controllerId());
        return new ClaimResult(granted,
                granted && authority.ownerChangedTick == currentTick, owner);
    }

    // Get the claim
    public static ClaimResult claim(@Nullable MinecraftServer server,
                                    @Nullable UUID assemblyKey,
                                    @Nullable UUID controllerId,
                                    int priority,
                                    long currentTick,
                                    long leaseTicks) {
        return claim(server, key(assemblyKey), controllerId, priority,
                currentTick, leaseTicks);
    }

    // Get the owner
    public static synchronized Optional<Owner> owner(@Nullable MinecraftServer server,
                                                     @Nullable String assemblyKey,
                                                     long currentTick) {
        String key = key(assemblyKey);
        if (server == null || key == null) {
            return Optional.empty();
        }
        Map<String, Authority> serverAuthorities = AUTHORITIES.get(server);
        Authority authority = serverAuthorities == null ? null : serverAuthorities.get(key);
        if (authority == null || !authority.advance(currentTick)) {
            return Optional.empty();
        }
        Owner owner = authority.liveOwner(currentTick);
        removeEmptyAuthority(server, key, serverAuthorities, authority);
        return Optional.ofNullable(owner);
    }

    // Get the owner
    public static Optional<Owner> owner(@Nullable MinecraftServer server,
                                        @Nullable UUID assemblyKey,
                                        long currentTick) {
        return owner(server, key(assemblyKey), currentTick);
    }

    // Check if this owns the value
    public static boolean owns(@Nullable MinecraftServer server,
                               @Nullable String assemblyKey,
                               @Nullable UUID controllerId,
                               long currentTick) {
        return controllerId != null && owner(server, assemblyKey, currentTick)
                .map(val -> controllerId.equals(val.controllerId())).orElse(false);
    }

    // Check if this owns the value
    public static boolean owns(@Nullable MinecraftServer server,
                               @Nullable UUID assemblyKey,
                               @Nullable UUID controllerId,
                               long currentTick) {
        return owns(server, key(assemblyKey), controllerId, currentTick);
    }

    // Release the SCM control authority API
    public static synchronized boolean release(@Nullable MinecraftServer server,
                                               @Nullable String assemblyKey,
                                               @Nullable UUID controllerId) {
        String key = key(assemblyKey);
        if (server == null || key == null || controllerId == null) {
            return false;
        }
        Map<String, Authority> serverAuthorities = AUTHORITIES.get(server);
        Authority authority = serverAuthorities == null ? null : serverAuthorities.get(key);
        if (authority == null) {
            return false;
        }
        boolean released = authority.candidates.remove(controllerId) != null;
        if (authority.owner != null && controllerId.equals(authority.owner.controllerId())) {
            authority.owner = null;
            released = true;
        }
        removeEmptyAuthority(server, key, serverAuthorities, authority);
        return released;
    }

    // Release the SCM control authority API
    public static boolean release(@Nullable MinecraftServer server,
                                  @Nullable UUID assemblyKey,
                                  @Nullable UUID controllerId) {
        return release(server, key(assemblyKey), controllerId);
    }

    // Release the controller
    public static synchronized int releaseController(@Nullable MinecraftServer server,
                                                     @Nullable UUID controllerId) {
        if (server == null || controllerId == null) {
            return 0;
        }
        Map<String, Authority> serverAuthorities = AUTHORITIES.get(server);
        if (serverAuthorities == null) {
            return 0;
        }
        int released = 0;
        Iterator<Map.Entry<String, Authority>> iterator = serverAuthorities.entrySet().iterator();
        while (iterator.hasNext()) {
            Authority authority = iterator.next().getValue();
            boolean changed = authority.candidates.remove(controllerId) != null;
            if (authority.owner != null && controllerId.equals(authority.owner.controllerId())) {
                authority.owner = null;
                changed = true;
            }
            if (changed) {
                released++;
            }
            if (authority.empty()) {
                iterator.remove();
            }
        }
        removeEmptyServer(server, serverAuthorities);
        return released;
    }

    // Get the prune
    public static synchronized int prune(@Nullable MinecraftServer server, long currentTick) {
        if (server == null) {
            return 0;
        }
        Map<String, Authority> serverAuthorities = AUTHORITIES.get(server);
        if (serverAuthorities == null) {
            return 0;
        }
        int before = serverAuthorities.size();
        Iterator<Authority> iterator = serverAuthorities.values().iterator();
        while (iterator.hasNext()) {
            Authority authority = iterator.next();
            authority.advance(currentTick);
            authority.liveOwner(currentTick);
            authority.candidates.values().removeIf(candidate -> candidate.expired(currentTick));
            if (authority.empty()) {
                iterator.remove();
            }
        }
        removeEmptyServer(server, serverAuthorities);
        return before - serverAuthorities.size();
    }

    // Clear the server
    public static synchronized void clearServer(@Nullable MinecraftServer server) {
        if (server != null) {
            AUTHORITIES.remove(server);
        }
    }

    // Check if the candidate outranks the current claim
    private static boolean outranks(Candidate candidate, Candidate current) {
        int priority = Integer.compare(candidate.priority(), current.priority());
        return priority > 0 || priority == 0
                && candidate.controllerId().toString()
                .compareTo(current.controllerId().toString()) < 0;
    }

    // Get the expiration
    private static long expiration(long currentTick, long leaseTicks) {
        long electionAndLease = leaseTicks == Long.MAX_VALUE
                ? Long.MAX_VALUE : leaseTicks + 1L;
        if (currentTick > Long.MAX_VALUE - electionAndLease) {
            return Long.MAX_VALUE;
        }
        return currentTick + electionAndLease;
    }

    // Handle key
    private static @Nullable String key(@Nullable String assemblyKey) {
        return assemblyKey == null || assemblyKey.isBlank() ? null : assemblyKey;
    }

    // Handle key
    private static @Nullable String key(@Nullable UUID assemblyKey) {
        return assemblyKey == null ? null : assemblyKey.toString();
    }

    // Remove the empty authority
    private static void removeEmptyAuthority(MinecraftServer server, String assemblyKey,
                                             Map<String, Authority> authorities,
                                             Authority authority) {
        if (authority.empty()) {
            authorities.remove(assemblyKey);
        }
        removeEmptyServer(server, authorities);
    }

    // Remove the empty server
    private static void removeEmptyServer(MinecraftServer server,
                                          Map<String, Authority> authorities) {
        if (authorities.isEmpty()) {
            AUTHORITIES.remove(server);
        }
    }

    // Apply SCM control authority
    private static final class Authority {
        // Tracked candidates
        private final Map<UUID, Candidate> candidates = new HashMap<>();
        // Candidate tick
        private long candidateTick = Long.MIN_VALUE;
        // Owner changed tick
        private long ownerChangedTick = Long.MIN_VALUE;
        // Current owner
        private @Nullable Owner owner;

        // Advance the authority
        private boolean advance(long currentTick) {
            if (candidateTick == Long.MIN_VALUE) {
                candidateTick = currentTick;
                liveOwner(currentTick);
                return true;
            }
            if (currentTick < candidateTick) {
                return false;
            }
            if (currentTick == candidateTick) {
                liveOwner(currentTick);
                return true;
            }

            Owner prev = liveOwner(currentTick);
            Candidate elected = candidates.values().stream()
                    .filter(candidate -> !candidate.expired(currentTick))
                    .reduce((best, candidate) -> outranks(candidate, best) ? candidate : best)
                    .orElse(null);
            candidates.clear();
            candidateTick = currentTick;
            if (elected != null) {
                owner = elected.asOwner();
            }
            if (!Objects.equals(prev == null ? null : prev.controllerId(),
                    owner == null ? null : owner.controllerId())) {
                ownerChangedTick = currentTick;
            }
            liveOwner(currentTick);
            return true;
        }

        // Get the live owner
        private @Nullable Owner liveOwner(long currentTick) {
            if (owner != null && owner.expired(currentTick)) {
                owner = null;
            }
            return owner;
        }

        // Check if the authority is empty
        private boolean empty() {
            return owner == null && candidates.isEmpty();
        }
    }

    // Store the candidate
    private record Candidate(UUID controllerId, int priority, long expiresAtTick) {
        // Check if the control claim expired
        private boolean expired(long currentTick) {
            return currentTick >= expiresAtTick;
        }

        // Get the candidate as owner
        private Owner asOwner() {
            return new Owner(controllerId, priority, expiresAtTick);
        }
    }

    // Store the owner
    public record Owner(UUID controllerId, int priority, long expiresAtTick) {
        // Check if the control claim expired
        public boolean expired(long currentTick) {
            return currentTick >= expiresAtTick;
        }
    }

    // Store claim results
    public record ClaimResult(boolean granted, boolean ownerChanged, @Nullable Owner owner) {
        // Get the denied
        private static ClaimResult denied(@Nullable Owner owner) {
            return new ClaimResult(false, false, owner);
        }
    }
}
