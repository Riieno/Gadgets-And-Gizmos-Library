package com.rieno.gadgetsandgizmos.lib.physics;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Verify detached lookup isolation, synchronization ownership and physics dispatch
class HostedBlockEntitiesTest{
    private BlockEntity host;
    private BlockEntity other;

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

    @AfterEach
    void release(){
        if(host != null) HostedBlockEntities.remove(host);
        if(other != null) HostedBlockEntities.remove(other);
    }

    @Test
    void hostedLookupLeavesWorldBlocksAndOtherLevelsAlone(){
        var level = mock(Level.class);
        host = component(level, BlockPos.ZERO);
        var child = component(level, BlockPos.ZERO.above());
        HostedBlockEntities.publish(host, List.of(child));
        assertSame(child, HostedBlockEntities.resolve(level, child.getBlockPos()));
        assertNull(level.getBlockEntity(child.getBlockPos()));
        assertNull(HostedBlockEntities.resolve(mock(Level.class), child.getBlockPos()));
        assertSame(host, HostedBlockEntities.host(child));
        other = component(level, BlockPos.ZERO.east());
        assertThrows(IllegalStateException.class, () -> HostedBlockEntities.publish(other, List.of(child)));
        HostedBlockEntities.remove(host);
        assertNull(HostedBlockEntities.resolve(level, child.getBlockPos()));
    }

    @Test
    void hostedIdentitiesCannotHideWorldBlockEntities(){
        var level = mock(Level.class);
        host = component(level, BlockPos.ZERO);
        var child = component(level, BlockPos.ZERO.above());
        when(level.getBlockEntity(child.getBlockPos())).thenReturn(mock(BlockEntity.class));
        assertFalse(HostedBlockEntities.positionAvailable(host, child.getBlockPos()));
        assertThrows(IllegalArgumentException.class, () -> HostedBlockEntities.publish(host, List.of(child)));
        assertNull(HostedBlockEntities.resolve(level, child.getBlockPos()));
        when(level.getBlockEntity(child.getBlockPos())).thenReturn(null);
        HostedBlockEntities.publish(host, List.of(child));
        when(level.getBlockEntity(child.getBlockPos())).thenReturn(mock(BlockEntity.class));
        assertNull(HostedBlockEntities.resolve(level, child.getBlockPos()));
    }

    @Test
    void slotIdentitiesRespectHeightAndWorldOccupancy(){
        var level = mock(Level.class);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        host = component(level, new BlockPos(0, 319, 0));
        assertEquals(new BlockPos(1, 318, 0), HostedBlockEntities.positionForSlot(host, 1, 8));
        when(level.getBlockEntity(new BlockPos(1, 318, 0))).thenReturn(mock(BlockEntity.class));
        int slot = HostedBlockEntities.findAvailableSlot(host, 1, 8, List.of(new BlockPos(2, 318, 0)));
        assertEquals(3, slot);
        other = component(level, new BlockPos(0, -64, 0));
        assertEquals(new BlockPos(1, -63, 0), HostedBlockEntities.positionForSlot(other, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> HostedBlockEntities.positionForSlot(host, 0, 8));
    }

    @Test
    void visualStateUpdatesStayInsideTheRealHost(){
        var level = mock(Level.class);
        host = component(level, BlockPos.ZERO);
        var child = component(level, BlockPos.ZERO.above());
        var state = mock(net.minecraft.world.level.block.state.BlockState.class);
        HostedBlockEntities.publish(host, List.of(child));
        assertTrue(HostedBlockEntities.updateState(level, child.getBlockPos(), state));
        verify(child).setBlockState(state);
        verify(host).setChanged();
        verify(level, never()).setBlock(any(), any(), anyInt());
        assertFalse(HostedBlockEntities.updateState(level, BlockPos.ZERO.east(), state));
    }

    @Test
    void dirtyStateAndSynchronizationBelongToTheRealHost(){
        var level = mock(Level.class);
        host = mock(SmartBlockEntity.class);
        when(host.getLevel()).thenReturn(level);
        var child = component(level, BlockPos.ZERO.above());
        HostedBlockEntities.publish(host, List.of(child));
        assertTrue(HostedBlockEntities.notifyHost(child, true));
        verify(host).setChanged();
        verify((SmartBlockEntity) host).sendData();
        when(host.isRemoved()).thenReturn(true);
        assertNull(HostedBlockEntities.host(child));
        assertTrue(HostedBlockEntities.notifyHost(child, true));
        verify(host, times(1)).setChanged();
    }

    @Test
    void nativePhysicsRunsOnceAndStopsAfterRelease(){
        var level = mock(Level.class);
        host = component(level, BlockPos.ZERO);
        var child = mock(BlockEntity.class, withSettings().extraInterfaces(BlockEntitySubLevelActor.class));
        when(child.getLevel()).thenReturn(level);
        when(child.getBlockPos()).thenReturn(BlockPos.ZERO.above());
        var body = mock(ServerSubLevel.class);
        var handle = mock(RigidBodyHandle.class);
        HostedBlockEntities.publish(host, List.of(child));
        HostedBlockEntities.physicsTick(host, body, handle, .05, component -> true);
        verify((BlockEntitySubLevelActor) child).sable$physicsTick(body, handle, .05);
        HostedBlockEntities.remove(host);
        HostedBlockEntities.physicsTick(host, body, handle, .05, component -> true);
        verify((BlockEntitySubLevelActor) child, times(1)).sable$physicsTick(body, handle, .05);
    }

    private static BlockEntity component(Level level, BlockPos pos){
        var res = mock(BlockEntity.class);
        when(res.getLevel()).thenReturn(level);
        when(res.getBlockPos()).thenReturn(pos);
        return res;
    }
}
