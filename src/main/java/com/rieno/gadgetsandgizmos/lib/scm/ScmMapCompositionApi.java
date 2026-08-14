package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Combine primary and attached SCM fragments without knowing the addon's map format
public final class ScmMapCompositionApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SCM map composition API
    private ScmMapCompositionApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the compose
    public static <T> Composition<T> compose(@Nullable Fragment<T> primary,
                                             @Nullable Collection<? extends Fragment<T>> attached,
                                             @Nullable Collection<UUID> connectedSubLevelIds) {
        LinkedHashSet<UUID> connected = orderedIds(connectedSubLevelIds);
        if (connected.isEmpty()) {
            return Composition.empty();
        }

        List<Fragment<T>> candidates = new ArrayList<>();
        if (attached != null) {
            for (Fragment<T> fragment : attached) {
                if (valid(fragment) && connected.contains(fragment.ownerSubLevelId())) {
                    candidates.add(fragment);
                }
            }
        }
        candidates.sort(fragmentOrder());

        List<Fragment<T>> ordered = new ArrayList<>();
        Set<UUID> acceptedOwners = new LinkedHashSet<>();
        Set<UUID> acceptedFragmentIds = new LinkedHashSet<>();
        List<UUID> rejected = new ArrayList<>();
        boolean primaryAccepted = valid(primary)
                && connected.contains(primary.ownerSubLevelId());
        if (primaryAccepted) {
            ordered.add(primary);
            acceptedOwners.add(primary.ownerSubLevelId());
            acceptedFragmentIds.add(primary.fragmentId());
        }
        for (Fragment<T> fragment : candidates) {
            if ((primaryAccepted && fragment == primary)
                    || acceptedOwners.contains(fragment.ownerSubLevelId())
                    || acceptedFragmentIds.contains(fragment.fragmentId())) {
                rejected.add(fragment.fragmentId());
                continue;
            }
            acceptedOwners.add(fragment.ownerSubLevelId());
            acceptedFragmentIds.add(fragment.fragmentId());
            ordered.add(fragment);
        }

        LinkedHashMap<UUID, UUID> ownerBySubLevel = new LinkedHashMap<>();
        List<SelectedFragment<T>> selections = new ArrayList<>();
        for (int idx = 0; idx < ordered.size(); idx++) {
            Fragment<T> fragment = ordered.get(idx);
            LinkedHashSet<UUID> effective = new LinkedHashSet<>();
            LinkedHashSet<UUID> overlaps = new LinkedHashSet<>();
            for (UUID subLevelId : fragment.coveredSubLevelIds()) {
                if (!connected.contains(subLevelId)) {
                    continue;
                }
                if (ownerBySubLevel.putIfAbsent(subLevelId, fragment.fragmentId()) == null) {
                    effective.add(subLevelId);
                } else {
                    overlaps.add(subLevelId);
                }
            }
            selections.add(new SelectedFragment<>(fragment, idx, idx == 0 && primaryAccepted,
                    immutableOrdered(effective), immutableOrdered(overlaps)));
        }

        LinkedHashSet<UUID> unclaimed = new LinkedHashSet<>(connected);
        unclaimed.removeAll(ownerBySubLevel.keySet());
        return new Composition<>(selections,
                Collections.unmodifiableMap(new LinkedHashMap<>(ownerBySubLevel)),
                immutableOrdered(unclaimed), List.copyOf(rejected),
                fingerprint(selections, connected));
    }

    // Select the SCM map composition API
    public static <T> Composition<T> select(@Nullable Fragment<T> primary,
                                            @Nullable Collection<? extends Fragment<T>> attached,
                                            @Nullable Collection<UUID> connectedSubLevelIds) {
        return compose(primary, attached, connectedSubLevelIds);
    }

    // Check if this is valid
    private static <T> boolean valid(@Nullable Fragment<T> fragment) {
        return fragment != null && fragment.fragmentId() != null
                && fragment.ownerSubLevelId() != null;
    }

    // Get the fragment order
    private static <T> Comparator<Fragment<T>> fragmentOrder() {
        return Comparator.comparing(Fragment<T>::ownerSubLevelId, UUID_ORDER)
                .thenComparing(Fragment<T>::fragmentId, UUID_ORDER);
    }

    // Get the ordered ids
    private static LinkedHashSet<UUID> orderedIds(@Nullable Collection<UUID> ids) {
        LinkedHashSet<UUID> res = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(java.util.Objects::nonNull).distinct().sorted(UUID_ORDER)
                    .forEach(res::add);
        }
        return res;
    }

    // Get the immutable ordered
    private static Set<UUID> immutableOrdered(Collection<UUID> ids) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }

    // Get the fingerprint
    private static String fingerprint(List<? extends SelectedFragment<?>> selections,
                                      Collection<UUID> connected) {
        long hash = 0xcbf29ce484222325L;
        for (UUID subLevelId : connected) {
            hash = hash(hash, "|c:" + subLevelId);
        }
        for (SelectedFragment<?> selection : selections) {
            hash = hash(hash, "|f:" + selection.fragment().fragmentId()
                    + ':' + selection.fragment().ownerSubLevelId());
            for (UUID subLevelId : selection.effectiveSubLevelIds()) {
                hash = hash(hash, "|o:" + subLevelId);
            }
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

    // Store the fragment
    public record Fragment<T>(@Nullable UUID fragmentId,
                              @Nullable UUID ownerSubLevelId,
                              Set<UUID> coveredSubLevelIds,
                              @Nullable T value) {
        // Initialize the fragment
        public Fragment {
            if (fragmentId == null) {
                fragmentId = ownerSubLevelId;
            }
            LinkedHashSet<UUID> normalized = orderedIds(coveredSubLevelIds);
            if (ownerSubLevelId != null) {
                normalized.add(ownerSubLevelId);
                normalized = orderedIds(normalized);
            }
            coveredSubLevelIds = immutableOrdered(normalized);
        }

        // Initialize the fragment
        public Fragment(@Nullable UUID fragmentId, @Nullable UUID ownerSubLevelId,
                        @Nullable Collection<UUID> coveredSubLevelIds, @Nullable T value) {
            this(fragmentId, ownerSubLevelId,
                    coveredSubLevelIds == null ? Set.of() : new LinkedHashSet<>(coveredSubLevelIds),
                    value);
        }

        // Initialize the fragment
        public Fragment(@Nullable UUID ownerSubLevelId,
                        @Nullable Collection<UUID> coveredSubLevelIds, @Nullable T value) {
            this(ownerSubLevelId, ownerSubLevelId, coveredSubLevelIds, value);
        }
    }

    // Store the selected fragment
    public record SelectedFragment<T>(Fragment<T> fragment, int order, boolean primary,
                                      Set<UUID> effectiveSubLevelIds,
                                      Set<UUID> overlappingSubLevelIds) {
        // Initialize the selected fragment
        public SelectedFragment {
            order = Math.max(0, order);
            effectiveSubLevelIds = effectiveSubLevelIds == null
                    ? Set.of() : immutableOrdered(effectiveSubLevelIds);
            overlappingSubLevelIds = overlappingSubLevelIds == null
                    ? Set.of() : immutableOrdered(overlappingSubLevelIds);
        }

        // Get the value
        public @Nullable T value() {
            return fragment == null ? null : fragment.value();
        }
    }

    // Store the composition
    public record Composition<T>(List<SelectedFragment<T>> fragments,
                                 Map<UUID, UUID> ownerFragmentBySubLevelId,
                                 Set<UUID> unclaimedConnectedSubLevelIds,
                                 List<UUID> rejectedFragmentIds,
                                 String fingerprint) {
        // Initialize the composition
        public Composition {
            fragments = fragments == null ? List.of() : List.copyOf(fragments);
            ownerFragmentBySubLevelId = ownerFragmentBySubLevelId == null
                    ? Map.of() : Collections.unmodifiableMap(
                            new LinkedHashMap<>(ownerFragmentBySubLevelId));
            unclaimedConnectedSubLevelIds = unclaimedConnectedSubLevelIds == null
                    ? Set.of() : immutableOrdered(unclaimedConnectedSubLevelIds);
            rejectedFragmentIds = rejectedFragmentIds == null
                    ? List.of() : List.copyOf(rejectedFragmentIds);
            fingerprint = fingerprint == null || fingerprint.isBlank()
                    ? "0000000000000000" : fingerprint;
        }

        // Get the owner
        public Optional<SelectedFragment<T>> owner(@Nullable UUID subLevelId) {
            UUID ownerId = subLevelId == null ? null
                    : ownerFragmentBySubLevelId.get(subLevelId);
            return ownerId == null ? Optional.empty() : fragments.stream()
                    .filter(fragment -> ownerId.equals(fragment.fragment().fragmentId()))
                    .findFirst();
        }

        // Get the values
        public List<T> values() {
            return fragments.stream().map(SelectedFragment::value)
                    .filter(java.util.Objects::nonNull).toList();
        }

        // Check if this owns the value
        public boolean owns(@Nullable UUID fragmentId, @Nullable UUID subLevelId) {
            return fragmentId != null && subLevelId != null
                    && fragmentId.equals(ownerFragmentBySubLevelId.get(subLevelId));
        }

        // Create an empty composition
        private static <T> Composition<T> empty() {
            return new Composition<>(List.of(), Map.of(), Set.of(), List.of(),
                    "0000000000000000");
        }
    }
}
