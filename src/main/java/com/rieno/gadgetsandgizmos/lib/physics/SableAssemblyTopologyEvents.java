package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

// Turn loaded Sable plot changes into shared topology revision updates
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID)
final class SableAssemblyTopologyEvents {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable assembly topology events
    private SableAssemblyTopologyEvents() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle block placement
    @SubscribeEvent
    public static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        invalidatePlot(event.getLevel(), event.getPos());
    }

    // Handle block removal
    @SubscribeEvent
    public static void blockBroken(BlockEvent.BreakEvent event) {
        invalidatePlot(event.getLevel(), event.getPos());
    }

    // Handle chunk loading
    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        invalidatePlotChunk(event.getLevel(), event.getChunk().getPos());
    }

    // Handle chunk unloading
    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        invalidatePlotChunk(event.getLevel(), event.getChunk().getPos());
    }

    // Handle level unloading
    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SableAssemblyTopologyInvalidation.forget(level);
        }
    }

    // Invalidate the plot
    private static void invalidatePlot(Object level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel
                && isPlotPosition(serverLevel, pos)) {
            SableAssemblyTopologyInvalidation.invalidate(serverLevel);
        }
    }

    // Invalidate the plot chunk
    private static void invalidatePlotChunk(Object level, net.minecraft.world.level.ChunkPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container != null && container.inBounds(pos)) {
            SableAssemblyTopologyInvalidation.invalidate(serverLevel);
        }
    }

    // Check if this is a plot position
    private static boolean isPlotPosition(ServerLevel level, BlockPos pos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.inBounds(pos);
    }
}
