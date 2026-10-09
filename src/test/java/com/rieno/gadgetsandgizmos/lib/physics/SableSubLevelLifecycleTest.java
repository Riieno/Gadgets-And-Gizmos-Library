package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SableSubLevelLifecycleTest{
    @BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(net.neoforged.fml.loading.LoadingModList.class)){
            var mods = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(mods);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }
    @Test void everyConnectedPlotUnloadsBeforeAnyNativeBodyIsRemoved(){
        var level = level();
        var first = body(level);
        var second = body(level);
        var container = mock(ServerSubLevelContainer.class);
        when(container.getSubLevel(first.getUniqueId())).thenReturn(first);
        when(container.getSubLevel(second.getUniqueId())).thenReturn(second);
        try(var containers = mockStatic(SubLevelContainer.class)){
            containers.when(() -> SubLevelContainer.getContainer(level)).thenReturn(container);
            SableSubLevelLifecycleApi.remove(level, List.of(first, second, first));
            var order = inOrder(first.getPlot(), second.getPlot(), container);
            order.verify(first.getPlot()).kickAllEntities();
            order.verify(second.getPlot()).kickAllEntities();
            order.verify(first.getPlot()).onRemove();
            order.verify(second.getPlot()).onRemove();
            order.verify(container).removeSubLevel(first, SubLevelRemovalReason.REMOVED);
            order.verify(container).removeSubLevel(second, SubLevelRemovalReason.REMOVED);
            verify(container, times(2)).removeSubLevel(any(ServerSubLevel.class), any());
        }
    }
    @Test void unavailableBodyCannotPartiallyUnloadAnAssembly(){
        var level = level();
        var first = body(level);
        var second = body(level);
        var container = mock(ServerSubLevelContainer.class);
        when(container.getSubLevel(first.getUniqueId())).thenReturn(first);
        try(var containers = mockStatic(SubLevelContainer.class)){
            containers.when(() -> SubLevelContainer.getContainer(level)).thenReturn(container);
            assertThrows(IllegalArgumentException.class, () -> SableSubLevelLifecycleApi.remove(level, List.of(first, second)));
            verifyNoInteractions(first.getPlot(), second.getPlot());
            verify(container, never()).removeSubLevel(any(ServerSubLevel.class), any());
        }
    }
    @Test void foreignThreadCannotMutatePlotsOrPhysics(){
        var level = level();
        when(level.getServer().isSameThread()).thenReturn(false);
        var body = body(level);
        assertThrows(IllegalStateException.class, () -> SableSubLevelLifecycleApi.remove(level, List.of(body)));
        verifyNoInteractions(body.getPlot());
    }
    private static ServerLevel level(){
        var level = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        return level;
    }
    private static ServerSubLevel body(ServerLevel level){
        var body = mock(ServerSubLevel.class);
        when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        when(body.getLevel()).thenReturn(level);
        when(body.getPlot()).thenReturn(mock(ServerLevelPlot.class));
        return body;
    }
}
