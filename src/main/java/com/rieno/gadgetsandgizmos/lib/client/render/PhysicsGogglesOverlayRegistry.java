package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Register extra layers for the physics goggles HUD
public final class PhysicsGogglesOverlayRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LoggerFactory.getLogger(PhysicsGogglesOverlayRegistry.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Registered overlay renderers
    private static final Map<ResourceLocation, Renderer> RENDERERS = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics goggles overlay registry
    private PhysicsGogglesOverlayRegistry() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register one overlay renderer
    public static synchronized void register(ResourceLocation id, Renderer renderer) {
        if (id == null || renderer == null) {
            throw new IllegalArgumentException("Physics goggles overlay id and renderer are required");
        }
        if (RENDERERS.putIfAbsent(id, renderer) != null) {
            throw new IllegalStateException("Physics goggles overlay already registered: " + id);
        }
    }

    // Register or replace one overlay renderer
    public static synchronized void registerOrReplace(ResourceLocation id, Renderer renderer) {
        if (id == null || renderer == null) {
            throw new IllegalArgumentException("Physics goggles overlay id and renderer are required");
        }
        RENDERERS.put(id, renderer);
    }

    // Remove one overlay renderer
    public static synchronized boolean unregister(ResourceLocation id) {
        return id != null && RENDERERS.remove(id) != null;
    }

    // Render every registered overlay
    public static void render(Context ctx) {
        Map<ResourceLocation, Renderer> renderers;
        synchronized (PhysicsGogglesOverlayRegistry.class) {
            renderers = Map.copyOf(RENDERERS);
        }
        renderers.forEach((id, renderer) -> {
            try {
                renderer.render(ctx);
            } catch (RuntimeException | LinkageError err) {
                LOGGER.error("Physics goggles overlay {} failed", id, err);
            }
        });
    }

    // Supply one physics goggles render frame
    public record Context(GuiGraphics graphics, LocalPlayer player, Level level,
                          @Nullable BlockEntity target, @Nullable UUID subLevelId,
                          float partialTick) {
        // Initialize the physics goggles render context
        public Context {
            if (graphics == null || player == null || level == null) {
                throw new IllegalArgumentException("Physics goggles render context is incomplete");
            }
        }
    }

    // Draw one registered physics goggles layer
    @FunctionalInterface
    public interface Renderer {
        // Render one physics goggles layer
        void render(Context ctx);
    }
}
