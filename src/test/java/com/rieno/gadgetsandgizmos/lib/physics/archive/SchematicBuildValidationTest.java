package com.rieno.gadgetsandgizmos.lib.physics.archive;

import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchematicBuildValidationTest{
    @BeforeAll static void registries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void validationYieldsAndResumesWithoutScanningTheWholeVolume(){
        var player = mock(ServerPlayer.class);
        var level = mock(ServerLevel.class);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        var border = mock(net.minecraft.world.level.border.WorldBorder.class);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        var validation = new SubLevelArchivePlacement.Validation(player, level, new AABB(0, 1, 0, 30, 20, 30));
        var calls = new AtomicInteger();
        try(var access = mockStatic(WorldAccessPolicy.class)){
            access.when(() -> WorldAccessPolicy.canAccess(eq(player), eq(level), isNull(), any())).thenReturn(true);
            assertFalse(validation.advance(() -> calls.getAndIncrement() < 3));
            verify(level, times(3)).getBlockState(any());
            assertFalse(validation.advance(() -> false));
            verify(level, times(3)).getBlockState(any());
            calls.set(0);
            assertFalse(validation.advance(() -> calls.getAndIncrement() < 2));
            verify(level, times(5)).getBlockState(any());
        }
    }
}
