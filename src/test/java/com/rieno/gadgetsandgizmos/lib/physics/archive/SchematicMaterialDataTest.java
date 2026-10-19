package com.rieno.gadgetsandgizmos.lib.physics.archive;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Material callbacks receive physical preview state while ordinary export keeps the complete program
class SchematicMaterialDataTest{
    @BeforeAll static void registries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void materialLoadingUsesTheEntityProjectionBeforeItsLoadCallback(){
        var be = mock(BlockEntity.class, withSettings().extraInterfaces(SchematicMaterialData.class));
        when(be.getBlockPos()).thenReturn(BlockPos.ZERO);
        var block = mock(Block.class, withSettings().extraInterfaces(EntityBlock.class));
        when(((EntityBlock)block).newBlockEntity(any(), any())).thenReturn(be);
        var state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        var level = mock(ServerLevel.class);
        CompoundTag saved = new CompoundTag(); saved.putString("Program", "large program");
        CompoundTag physical = new CompoundTag(); physical.putString("Attachment", "module");
        when(((SchematicMaterialData)be).schematicMaterialData(saved)).thenReturn(physical);

        assertSame(be, SchematicBlockData.create(level, BlockPos.ZERO, state, saved, true));
        var loaded = ArgumentCaptor.forClass(CompoundTag.class);
        verify(be).loadWithComponents(loaded.capture(), isNull());
        assertEquals(physical, loaded.getValue());
        assertFalse(loaded.getValue().contains("Program"));

        reset(be);
        when(be.getBlockPos()).thenReturn(BlockPos.ZERO);
        SchematicBlockData.create(level, BlockPos.ZERO, state, saved);
        verify((SchematicMaterialData)be, never()).schematicMaterialData(any());
        verify(be).loadWithComponents(eq(saved), isNull());
    }
}
