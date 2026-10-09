package com.rieno.gadgetsandgizmos.lib.discovery;

import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubLevelActorLookupTest{
    @Test
    void ordinaryBlockFacesDoNotScanUnrelatedActors() throws Exception{
        LevelPlot plot = mock(LevelPlot.class, withSettings().extraInterfaces(SubLevelActorLookup.class));
        var method = SubLevelBlockEntityCollector.class.getDeclaredMethod("findActorBlockEntity", LevelPlot.class, BlockPos.class);
        method.setAccessible(true);
        for(int idx = 0; idx < 100; idx++){
            assertNull(method.invoke(null, plot, new BlockPos(idx, 0, 0)));
        }
        verify(plot, never()).getBlockEntityActors();
    }

    @Test
    void readsLiveReplacementsAndRetainsTheFallbackForRemovedActors() throws Exception{
        LevelPlot plot = mock(LevelPlot.class, withSettings().extraInterfaces(SubLevelActorLookup.class));
        var lookup = (SubLevelActorLookup) plot;
        var pos = new BlockPos(1, 2, 3);
        BlockEntity first = mock(BlockEntity.class);
        BlockEntity replacement = mock(BlockEntity.class);
        when(first.getBlockPos()).thenReturn(pos);
        when(replacement.getBlockPos()).thenReturn(pos);
        when(lookup.gadgetsngizmos$actorBlockEntity(pos)).thenReturn(first, replacement, first);
        var method = SubLevelBlockEntityCollector.class.getDeclaredMethod("findActorBlockEntity", LevelPlot.class, BlockPos.class);
        method.setAccessible(true);
        assertSame(first, method.invoke(null, plot, pos));
        assertSame(replacement, method.invoke(null, plot, pos));
        verify(plot, never()).getBlockEntityActors();
        when(first.isRemoved()).thenReturn(true);
        when(plot.getBlockEntityActors()).thenReturn(List.of());
        assertNull(method.invoke(null, plot, pos));
        verify(plot, times(1)).getBlockEntityActors();
    }
}
