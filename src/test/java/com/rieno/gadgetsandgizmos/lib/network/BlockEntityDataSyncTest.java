package com.rieno.gadgetsandgizmos.lib.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockEntityDataSyncTest{
    @Test
    void queuedVisualUpdatesCoalesceAndStopWhenTheBlockIsReplaced(){
        var server = mock(MinecraftServer.class);
        var level = mock(ServerLevel.class);
        var component = mock(BlockEntity.class);
        var tasks = new ArrayList<Runnable>();
        when(component.getLevel()).thenReturn(level);
        when(component.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(level.getServer()).thenReturn(server);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(component);
        doAnswer(ctx -> { tasks.add(ctx.getArgument(0)); return null; }).when(server).execute(any(Runnable.class));
        BlockEntityDataSync.enqueue(component);
        BlockEntityDataSync.enqueue(component);
        assertEquals(1, tasks.size());
        verify(component, never()).getUpdatePacket();
        tasks.removeFirst().run();
        verify(component).getUpdatePacket();
        BlockEntityDataSync.enqueue(component);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(mock(BlockEntity.class));
        tasks.removeFirst().run();
        verify(component, times(1)).getUpdatePacket();
        verify(level, never()).sendBlockUpdated(any(), any(), any(), anyInt());
    }
}
