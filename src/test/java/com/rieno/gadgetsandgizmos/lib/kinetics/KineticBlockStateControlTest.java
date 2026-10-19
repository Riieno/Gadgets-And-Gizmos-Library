package com.rieno.gadgetsandgizmos.lib.kinetics;

import com.rieno.gadgetsandgizmos.lib.control.BlockStateControlReceiver;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockStateDataAccess;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KineticBlockStateControlTest{
    @BeforeAll
    static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void stateChangesRebuildKineticsSynchronously(){
        var target = mock(KineticBlockEntity.class);
        var level = mock(Level.class);
        var current = Blocks.LEVER.defaultBlockState();
        var updated = current.setValue(BlockStateProperties.POWERED, true);
        when(target.getLevel()).thenReturn(level);
        when(target.getBlockState()).thenReturn(current);
        when(target.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(level.setBlock(BlockPos.ZERO, updated, 2)).thenReturn(true);
        assertTrue(KineticBlockStateControl.apply(target, updated));
        var ordered = inOrder(target, level);
        ordered.verify(target).detachKinetics();
        ordered.verify(level).setBlock(BlockPos.ZERO, updated, 2);
        ordered.verify(target).attachKinetics();
        ordered.verify(target).setChanged();
        ordered.verify(target).sendData();
        verify(level, never()).scheduleTick(any(), any(net.minecraft.world.level.block.Block.class), anyInt());
    }

    @Test
    void failedStatePublicationStillRestoresTheNetwork(){
        var target = mock(KineticBlockEntity.class);
        when(target.getLevel()).thenReturn(mock(Level.class));
        when(target.getBlockState()).thenReturn(Blocks.LEVER.defaultBlockState());
        when(target.getBlockPos()).thenReturn(BlockPos.ZERO);
        assertFalse(KineticBlockStateControl.apply(target,
                Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true)));
        verify(target).attachKinetics();
        verify(target, never()).sendData();
        assertFalse(KineticBlockStateControl.apply(target, Blocks.STONE.defaultBlockState()));
    }

    @Test
    void statePortsDelegateToTheTargetsControlLifecycle(){
        var target = mock(KineticBlockEntity.class, withSettings().extraInterfaces(BlockStateControlReceiver.class));
        var receiver = (BlockStateControlReceiver)target;
        var level = mock(Level.class);
        var current = Blocks.LEVER.defaultBlockState();
        var updated = current.setValue(BlockStateProperties.POWERED, true);
        when(level.isLoaded(BlockPos.ZERO)).thenReturn(true);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(current);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(target);
        when(receiver.applyControlledBlockState(updated)).thenReturn(true);
        BlockStateDataAccess.registerWritableProperty(Blocks.LEVER, "powered");
        assertTrue(BlockStateDataAccess.write(level, BlockPos.ZERO, "state_powered", GraphValue.bool(true)));
        verify(receiver).applyControlledBlockState(updated);
        verify(level, never()).setBlock(any(), any(), anyInt());
        verify(level, never()).updateNeighborsAt(any(), any());
    }
}
