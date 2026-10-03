package com.rieno.gadgetsandgizmos.lib.client.render;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.RenderBuffers;

// Keep queued world geometry ahead of particles that precede translucent blocks
public final class ParticleRenderOrdering {
    private static volatile List<BeforeRule> rules = List.of();
    private static volatile Set<ParticleRenderType> afterClouds = Set.of();

    private record BeforeRule(ParticleRenderType first, ParticleRenderType second) {
    }

    private ParticleRenderOrdering() {
    }

    // Finish entity and block entity batches before early particles render
    public static void flushDeferredGeometry(RenderBuffers buffers) {
        buffers.bufferSource().endBatch();
    }

    // Register a particle layer that must draw before another layer
    public static synchronized void registerBefore(ParticleRenderType first, ParticleRenderType second) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        if (first == second) throw new IllegalArgumentException("Particle layers must differ");
        List<BeforeRule> updated = new ArrayList<>(rules);
        BeforeRule rule = new BeforeRule(first, second);
        if (!updated.contains(rule)) updated.add(rule);
        rules = List.copyOf(updated);
    }

    // Register a layer for the late vanilla particle pass
    public static synchronized void registerAfterClouds(ParticleRenderType type) {
        Objects.requireNonNull(type);
        Set<ParticleRenderType> updated = new LinkedHashSet<>(afterClouds);
        updated.add(type);
        afterClouds = Set.copyOf(updated);
    }

    // Use the late pass only outside shader pipelines
    public static boolean rendersAfterClouds(ParticleRenderType type) {
        return afterClouds.contains(type) && !SoftParticleRenderTypes.isShaderPackActive();
    }

    // Check whether the later world pass has any layers
    public static boolean hasAfterCloudsLayers() {
        return !afterClouds.isEmpty();
    }

    // Apply registered layer order without changing the engine's particle queues
    public static Set<ParticleRenderType> order(Set<ParticleRenderType> types) {
        if (rules.isEmpty()) return types;
        List<ParticleRenderType> ordered = new ArrayList<>(types);
        boolean changed = false;
        for (BeforeRule rule : rules) {
            int firstIndex = ordered.indexOf(rule.first());
            int secondIndex = ordered.indexOf(rule.second());
            if (firstIndex < 0 || secondIndex < 0 || firstIndex < secondIndex) continue;
            ordered.remove(firstIndex);
            ordered.add(secondIndex, rule.first());
            changed = true;
        }
        return changed ? new LinkedHashSet<>(ordered) : types;
    }
}
