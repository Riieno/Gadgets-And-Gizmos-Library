package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

// Retain independent visibility for each client render owner and presentation context
@EventBusSubscriber(modid = "gadgetsngizmos", value = Dist.CLIENT)
public final class RenderVisibilityGate<K> implements AutoCloseable{
    private static final Set<RenderVisibilityGate<?>> GATES = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<K, Map<Object, Entry>> entries = new IdentityHashMap<>();

    // Probe local bounds on every presentation; callers bypass this for GUI and secondary camera renders
    public boolean shouldRender(K owner, Object ctx, PoseStack poses, MultiBufferSource buffers, AABB bounds){
        GATES.add(this);
        Entry entry = entries.computeIfAbsent(owner, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(ctx, ignored -> new Entry());
        entry.lastUse = Util.getMillis();
        return entry.visibility.probe(poses, buffers, bounds);
    }

    // Retire queries on the render thread when a world closes or a presentation disappears
    @SubscribeEvent
    public static void endFrame(RenderFrameEvent.Post evt){
        long now = Util.getMillis();
        boolean clear = Minecraft.getInstance().level == null;
        GATES.removeIf(gate -> {
            gate.entries.values().removeIf(contexts -> {
                contexts.values().removeIf(entry -> {
                    if(!clear && now - entry.lastUse <= 5_000L) return false;
                    entry.visibility.close();
                    return true;
                });
                return contexts.isEmpty();
            });
            return gate.entries.isEmpty();
        });
    }

    // Clear retained presentations; the gate can be used again after a resource reload
    @Override
    public void close(){
        entries.values().forEach(contexts -> contexts.values().forEach(entry -> entry.visibility.close()));
        entries.clear();
        GATES.remove(this);
    }

    private static final class Entry{
        private final RenderSurfaceVisibility visibility = new RenderSurfaceVisibility();
        private long lastUse;
    }
}
