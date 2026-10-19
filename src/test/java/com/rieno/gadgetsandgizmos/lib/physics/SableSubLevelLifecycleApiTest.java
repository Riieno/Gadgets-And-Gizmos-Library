package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Keep transfer scopes balanced when nested disassembly fails
class SableSubLevelLifecycleApiTest{
    // Initialize vanilla registries before mocking level and body classes
    @BeforeAll static void prepareRegistries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    // Defer plot removal until the outermost transfer has finished even after an exception
    @Test void nestedFailuresReleaseTheBodyAndRefreshBoundsOnce(){
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        ServerSubLevel body = mock(ServerSubLevel.class);
        when(level.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(body.getLevel()).thenReturn(level);
        assertThrows(IllegalStateException.class, () -> SableSubLevelLifecycleApi.transfer(level, body, () -> {
            assertTrue(SableSubLevelLifecycleApi.isTransferring(body));
            SableSubLevelLifecycleApi.transfer(level, body, () -> {
                assertTrue(SableSubLevelLifecycleApi.isTransferring(body));
                verify(body, never()).onPlotBoundsChanged();
                throw new IllegalStateException("transfer failed");
            });
        }));
        assertFalse(SableSubLevelLifecycleApi.isTransferring(body));
        verify(body, times(1)).onPlotBoundsChanged();
    }
}
