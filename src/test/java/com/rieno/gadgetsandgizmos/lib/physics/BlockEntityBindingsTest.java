package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Physical bindings must preserve block lifetime and reject conflicting hosts atomically
class BlockEntityBindingsTest{
    @BeforeAll
    static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(net.neoforged.fml.loading.LoadingModList.class)){
            var mods = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(mods);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }

    @Test
    void removingBindingsDoesNotRemovePhysicalBlocks(){
        var level = mock(Level.class);
        var host = component(level);
        var block = component(level);
        BlockEntityBindings.replace(host, List.of(block));
        assertSame(host, BlockEntityBindings.host(block));
        BlockEntityBindings.remove(host);
        assertNull(BlockEntityBindings.host(block));
        verify(block, never()).setRemoved();
        verifyNoInteractions(level);
    }

    @Test
    void ownershipFailuresKeepBothPreviousBindings(){
        var level = mock(Level.class);
        var host = component(level);
        var other = component(level);
        var first = component(level);
        var second = component(level);
        try{
            BlockEntityBindings.replace(host, List.of(first));
            BlockEntityBindings.replace(other, List.of(second));
            assertThrows(IllegalStateException.class, () -> BlockEntityBindings.replace(other, List.of(second, first)));
            assertSame(host, BlockEntityBindings.host(first));
            assertSame(other, BlockEntityBindings.host(second));
            assertThrows(IllegalArgumentException.class, () -> BlockEntityBindings.replace(host, List.of(component(mock(Level.class)))));
        }finally{
            BlockEntityBindings.remove(host);
            BlockEntityBindings.remove(other);
        }
    }

    private static BlockEntity component(Level level){
        var res = mock(BlockEntity.class);
        when(res.getLevel()).thenReturn(level);
        return res;
    }

    // Topology discovery must not switch stable matches or claim reserved and foreign blocks
    @Test
    void selectionRetainsMatchesAndRejectsUnavailableComponents(){
        var level = mock(Level.class);
        var host = component(level);
        var other = component(level);
        var first = component(level);
        var second = component(level);
        var foreign = component(level);
        var unloaded = component(level);
        when(host.getBlockPos()).thenReturn(net.minecraft.core.BlockPos.ZERO);
        when(first.getBlockPos()).thenReturn(new net.minecraft.core.BlockPos(2, 0, 0));
        when(second.getBlockPos()).thenReturn(new net.minecraft.core.BlockPos(6, 0, 0));
        when(foreign.getBlockPos()).thenReturn(new net.minecraft.core.BlockPos(1, 0, 0));
        when(unloaded.isRemoved()).thenReturn(true);
        try{
            BlockEntityBindings.replace(other, List.of(foreign));
            var roster = List.of(first, second, foreign, unloaded);
            assertSame(first, BlockEntityBindings.select(host, roster, null, List.of(), val -> true));
            assertSame(second, BlockEntityBindings.select(host, roster, second, List.of(), val -> true));
            assertSame(second, BlockEntityBindings.select(host, roster, first, List.of(first), val -> true));
            assertNull(BlockEntityBindings.select(host, roster, first, List.of(first, second), val -> true));
            assertNull(BlockEntityBindings.select(host, roster, null, List.of(), val -> val == foreign));
            verify(first, never()).setRemoved();
        }finally{ BlockEntityBindings.remove(other); }
    }
}
