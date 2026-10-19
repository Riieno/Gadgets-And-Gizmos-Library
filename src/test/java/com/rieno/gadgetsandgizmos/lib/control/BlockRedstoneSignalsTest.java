package com.rieno.gadgetsandgizmos.lib.control;

import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlock;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Read physical strengths without depending on private state fields or neighbouring blocks
class BlockRedstoneSignalsTest{
    @BeforeAll static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void analogueLeverKeepsSixOnEveryFaceAndAfterItsStateChanges(){
        var level = mock(Level.class);
        var pos = new BlockPos(2, 80, 3);
        var block = mock(AnalogLeverBlock.class, CALLS_REAL_METHODS);
        var state = mock(BlockState.class);
        var type = mock(BlockEntityType.class);
        when(type.isValid(any())).thenReturn(true);
        var lever = new AnalogLeverBlockEntity(type, pos, Blocks.LEVER.defaultBlockState());
        when(state.getSignal(eq(level), eq(pos), any())).thenAnswer(call ->
                block.getSignal(state, level, pos, call.getArgument(2)));
        when(level.isLoaded(pos)).thenReturn(true);
        when(level.getBlockState(pos)).thenReturn(state);
        when(level.getBlockEntity(pos)).thenReturn(lever);
        for(int idx = 0; idx < 6; idx++) lever.changeState(false);
        assertEquals(6, BlockRedstoneSignals.read(level, pos, null));
        for(Direction face : Direction.values()) assertEquals(6, BlockRedstoneSignals.read(level, pos, face));
        lever.changeState(true);
        assertEquals(5, BlockRedstoneSignals.read(level, pos, null));
        verify(level, never()).getSignal(any(), any());
    }

    @Test void receiverReadsReceivedPowerWhileTransmitterReadsTransmittedPower(){
        var level = mock(Level.class);
        var pos = BlockPos.ZERO;
        var state = mock(BlockState.class);
        var link = mock(RedstoneLinkBlockEntity.class);
        when(level.isLoaded(pos)).thenReturn(true);
        when(level.getBlockState(pos)).thenReturn(state);
        when(level.getBlockEntity(pos)).thenReturn(link);
        when(state.hasProperty(RedstoneLinkBlock.RECEIVER)).thenReturn(true);
        when(state.getValue(RedstoneLinkBlock.RECEIVER)).thenReturn(true);
        when(link.getReceivedSignal()).thenReturn(6);
        when(link.getSignal()).thenReturn(0);
        assertEquals(6, BlockRedstoneSignals.read(level, pos, null));
        assertEquals(6, BlockRedstoneSignals.read(level, pos, Direction.EAST));
        when(state.getValue(RedstoneLinkBlock.RECEIVER)).thenReturn(false);
        when(link.getSignal()).thenReturn(11);
        assertEquals(11, BlockRedstoneSignals.read(level, pos, null));
    }

    @Test void outwardFacesUseMinecraftQueryDirectionAndUnloadedTargetsAreUnavailable(){
        var level = mock(Level.class);
        var state = mock(BlockState.class);
        var pos = BlockPos.ZERO;
        when(state.getDirectSignal(level, pos, Direction.WEST)).thenReturn(6);
        assertEquals(6, BlockRedstoneSignals.readFace(level, pos, state, Direction.EAST));
        assertEquals(0, BlockRedstoneSignals.read(level, pos, null));
        verify(level, never()).getBlockState(any());
    }
}
