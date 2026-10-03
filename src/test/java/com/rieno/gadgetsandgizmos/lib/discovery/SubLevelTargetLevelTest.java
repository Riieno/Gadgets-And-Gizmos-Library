package com.rieno.gadgetsandgizmos.lib.discovery;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubLevelTargetLevelTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        try (var loader = mockStatic(net.neoforged.fml.loading.LoadingModList.class)) {
            var mods = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(mods.getModFiles()).thenReturn(java.util.List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(mods);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }

    @Test
    void blockWithoutBlockEntityResolvesItsOwningLevelForEverySubLevelUuid() {
        ServerLevel world = mock(ServerLevel.class);
        ServerSubLevelContainer container = mock(ServerSubLevelContainer.class);
        try (var containers = mockStatic(SubLevelContainer.class)) {
            containers.when(() -> SubLevelContainer.getContainer(world)).thenReturn(container);
            assertSame(world, SubLevelBlockEntityCollector.resolveTargetLevel(world, null));
            for (int depth = 0; depth < 3; depth++) {
                UUID id = UUID.randomUUID();
                ServerSubLevel body = mock(ServerSubLevel.class);
                when(body.getLevel()).thenReturn(world);
                when(container.getSubLevel(id)).thenReturn(body);
                assertSame(world, SubLevelBlockEntityCollector.resolveTargetLevel(world, id));
                when(body.isRemoved()).thenReturn(true);
                assertNull(SubLevelBlockEntityCollector.resolveTargetLevel(world, id));
            }
            assertNull(SubLevelBlockEntityCollector.resolveTargetLevel(world, UUID.randomUUID()));
        }
    }
}
