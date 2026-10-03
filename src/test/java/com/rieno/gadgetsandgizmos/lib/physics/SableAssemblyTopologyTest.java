package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.scm.ScmMapCompositionApi;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SableAssemblyTopologyTest {
    @BeforeAll
    static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        try(MockedStatic<net.neoforged.fml.loading.LoadingModList> loader =
                    mockStatic(net.neoforged.fml.loading.LoadingModList.class)){
            var modList = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(modList.getModFiles()).thenReturn(List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(modList);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }

    @Test
    void retainsNestedOptionalMotorsThroughAssemblyMapComposition(){
        ServerLevel level = mock(ServerLevel.class);
        ServerSubLevel root = body(level);
        ServerSubLevel thigh = body(level);
        ServerSubLevel shin = body(level);
        ServerSubLevel unrelated = body(level);
        ServerSubLevelContainer container = mock(ServerSubLevelContainer.class);
        when(container.getAllSubLevels()).thenAnswer(call -> List.of(root, thigh, shin, unrelated));
        var hip = new SableAssemblyTopologyApi.Edge(root.getUniqueId(), thigh.getUniqueId(),
                SableAssemblyConnection.Kind.STRUCTURAL);
        var knee = new SableAssemblyTopologyApi.Edge(shin.getUniqueId(), thigh.getUniqueId(),
                SableAssemblyConnection.Kind.STRUCTURAL);
        var unloaded = new SableAssemblyTopologyApi.Edge(shin.getUniqueId(), UUID.randomUUID(),
                SableAssemblyConnection.Kind.STRUCTURAL);
        try(MockedStatic<SubLevelContainer> containers = mockStatic(SubLevelContainer.class)){
            containers.when(() -> SubLevelContainer.getContainer(level)).thenReturn(container);
            var before = SableAssemblyTopologyApi.discover(root);
            assertEquals(Set.of(root.getUniqueId()), before.loadedBodyIds());
            var topology = SableAssemblyTopologyApi.discover(root, null, null, List.of(knee, hip, unloaded));
            Set<UUID> expected = Set.of(root.getUniqueId(), thigh.getUniqueId(), shin.getUniqueId());
            assertTrue(topology.available());
            assertEquals(expected, topology.loadedBodyIds());
            assertEquals(2, topology.edges().size());
            assertEquals(1, topology.carriagePartitions().size());
            assertNotEquals(before.fingerprint(), topology.fingerprint());
            var fragment = new ScmMapCompositionApi.Fragment<>(UUID.randomUUID(), root.getUniqueId(),
                    topology.carriagePartitions().getFirst().bodyIds(), "assigned motors");
            var composed = ScmMapCompositionApi.compose(fragment, List.of(), topology.loadedBodyIds());
            assertEquals(expected, composed.fragments().getFirst().effectiveSubLevelIds());
            var detached = SableAssemblyTopologyApi.discover(root, null, null, List.of(hip));
            assertEquals(Set.of(root.getUniqueId(), thigh.getUniqueId()), detached.loadedBodyIds());
            assertNotEquals(topology.fingerprint(), detached.fingerprint());
        }
    }

    private static ServerSubLevel body(ServerLevel level){
        ServerSubLevel body = mock(ServerSubLevel.class);
        ServerLevelPlot plot = mock(ServerLevelPlot.class);
        when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        when(body.getLevel()).thenReturn(level);
        when(body.getPlot()).thenReturn(plot);
        when(plot.getBlockEntityActors()).thenReturn(List.of());
        return body;
    }
}
