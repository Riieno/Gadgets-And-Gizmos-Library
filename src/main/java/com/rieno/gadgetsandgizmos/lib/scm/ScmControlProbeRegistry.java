package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Register addon control probes which an SCM can discover through linked blocks
public final class ScmControlProbeRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SCM control probe
    private ScmControlProbeRegistry() {
    }

    // Register the SCM control probe
    public static synchronized void register(
            ResourceLocation id, int priority, Factory factory
    ) {
        ENTRIES.put(Objects.requireNonNull(id, "id"),
                new Entry(priority, Objects.requireNonNull(factory, "factory")));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remove the SCM control probe
    public static synchronized void unregister(ResourceLocation id) {
        ENTRIES.remove(id);
    }

    // Create the SCM control probe
    public static List<ScmControlProbe> create(
            Level level, @Nullable BlockEntity blockEntity,
            ScmTarget target, Vec3 suggestedDirection
    ) {
        return create(level, blockEntity, target, suggestedDirection, List.of(target));
    }

    // Create the SCM control probe
    public static List<ScmControlProbe> create(
            Level level, @Nullable BlockEntity blockEntity,
            ScmTarget target, Vec3 suggestedDirection,
            List<ScmTarget> linkedTargets
    ) {
        if (level == null || blockEntity != null && blockEntity.isRemoved() || target == null) {
            return List.of();
        }
        Context ctx = new Context(level, target,
                suggestedDirection == null ? Vec3.ZERO : suggestedDirection,
                linkedTargets == null ? List.of(target) : List.copyOf(linkedTargets));
        List<Entry> providers;
        synchronized (ScmControlProbeRegistry.class) {
            providers = ENTRIES.values().stream()
                    .sorted(Comparator.comparingInt(Entry::priority).reversed())
                    .toList();
        }
        List<ScmControlProbe> res = new ArrayList<>();
        for (Entry provider : providers) {
            List<ScmControlProbe> probes = provider.factory().create(blockEntity, ctx);
            if (probes != null) {
                probes.stream().filter(Objects::nonNull).forEach(res::add);
            }
        }
        return List.copyOf(res);
    }

    // Pass target-sublevel coordinates and linked controls to one probe factory
    public record Context(Level level, ScmTarget target, Vec3 suggestedDirection,
                          List<ScmTarget> linkedTargets) {
        // Initialize the context
        public Context {
            linkedTargets = linkedTargets == null ? List.of() : List.copyOf(linkedTargets);
        }
    }

    // Expose the factory
    @FunctionalInterface
    public interface Factory {
        // Create the factory
        List<ScmControlProbe> create(@Nullable BlockEntity blockEntity, Context ctx);
    }

    // Store the entry
    private record Entry(int priority, Factory factory) {
    }
}
