package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Keep tablet app definitions ordered and addressable by stable id
public final class TabletAppRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Object MUTEX = new Object();
    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared tablet app state
    private static volatile State state = new State(new Snapshot(0L, List.of()), Map.of());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the tablet app
    private TabletAppRegistry() {
    }

    // Register the tablet app
    public static void register(TabletAppDefinition definition, TabletActionHandler handler) {
        Entry entry = entry(definition, handler);
        synchronized (MUTEX) {
            if (ENTRIES.putIfAbsent(definition.id(), entry) != null) {
                throw new IllegalStateException("Tablet app already registered: " + definition.id());
            }
            publish();
        }
    }

    // Register the tablet app if absent
    public static boolean registerIfAbsent(TabletAppDefinition definition,
                                           TabletActionHandler handler) {
        Entry entry = entry(definition, handler);
        synchronized (MUTEX) {
            if (ENTRIES.putIfAbsent(definition.id(), entry) != null) {
                return false;
            }
            publish();
            return true;
        }
    }

    // Register the replace
    public static @Nullable TabletAppDefinition registerOrReplace(TabletAppDefinition definition,
                                                                  TabletActionHandler handler) {
        Entry entry = entry(definition, handler);
        synchronized (MUTEX) {
            Entry prev = ENTRIES.put(definition.id(), entry);
            publish();
            return prev == null ? null : prev.definition();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remove the tablet app
    public static boolean unregister(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        synchronized (MUTEX) {
            if (ENTRIES.remove(id) == null) {
                return false;
            }
            publish();
            return true;
        }
    }

    // Get the apps
    public static List<TabletAppDefinition> apps() {
        return state.snapshot().apps();
    }

    // Get the snapshot
    public static Snapshot snapshot() {
        return state.snapshot();
    }

    // Get the revision
    public static long revision() {
        return state.snapshot().revision();
    }

    // Get the definition
    public static @Nullable TabletAppDefinition definition(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return state.snapshot().apps().stream()
                .filter(definition -> definition.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    // Get the handler
    public static @Nullable TabletActionHandler handler(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return state.handlers().get(id);
    }

    // Get the entry
    private static Entry entry(TabletAppDefinition definition, TabletActionHandler handler) {
        return new Entry(Objects.requireNonNull(definition, "definition"),
                Objects.requireNonNull(handler, "handler"));
    }

    // Publish the tablet app
    private static void publish() {
        List<Entry> ordered = ENTRIES.values().stream()
                .sorted(Comparator.comparing(entry -> entry.definition().id().toString()))
                .toList();
        List<TabletAppDefinition> apps = ordered.stream().map(Entry::definition).toList();
        Map<ResourceLocation, TabletActionHandler> handlers = new LinkedHashMap<>();
        ordered.forEach(entry -> handlers.put(entry.definition().id(), entry.handler()));
        state = new State(new Snapshot(state.snapshot().revision() + 1L, apps), Map.copyOf(handlers));
    }

    // Store the entry
    private record Entry(TabletAppDefinition definition, TabletActionHandler handler) {
    }

    // Store the current state
    private record State(Snapshot snapshot, Map<ResourceLocation, TabletActionHandler> handlers) {
    }

    // Store the snapshot
    public record Snapshot(long revision, List<TabletAppDefinition> apps) {
        // Initialize the snapshot
        public Snapshot {
            apps = apps == null ? List.of() : List.copyOf(apps);
        }
    }
}
