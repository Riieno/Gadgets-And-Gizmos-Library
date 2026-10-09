package com.rieno.gadgetsandgizmos.lib.discovery;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SableSubLevelResidencyTest{
    @Test void sparseRestoredPlotsAreReadyWithoutCreatingReservedEmptyChunks(){
        var body = mock(ServerSubLevel.class);
        var plot = mock(ServerLevelPlot.class);
        var holder = mock(PlotChunkHolder.class);
        when(body.getPlot()).thenReturn(plot);
        when(plot.getLoadedChunks()).thenReturn(List.of(holder));
        when(holder.getChunk()).thenReturn(mock(LevelChunk.class));
        assertTrue(SableSubLevelResidency.isFullyLoaded(body));
        assertTrue(SableSubLevelResidency.areFullyLoaded(List.of(body)));
    }

    @Test void missingPlotsRemovedBodiesAndUnrestoredChunksRemainUnavailable(){
        var body = mock(ServerSubLevel.class);
        assertFalse(SableSubLevelResidency.isFullyLoaded(body));
        var plot = mock(ServerLevelPlot.class);
        when(body.getPlot()).thenReturn(plot);
        when(plot.getLoadedChunks()).thenReturn(List.of());
        assertFalse(SableSubLevelResidency.isFullyLoaded(body));
        var holder = mock(PlotChunkHolder.class);
        when(plot.getLoadedChunks()).thenReturn(List.of(holder));
        assertFalse(SableSubLevelResidency.isFullyLoaded(body));
        when(holder.getChunk()).thenReturn(mock(LevelChunk.class));
        when(body.isRemoved()).thenReturn(true);
        assertFalse(SableSubLevelResidency.areFullyLoaded(List.of(body)));
    }
}
