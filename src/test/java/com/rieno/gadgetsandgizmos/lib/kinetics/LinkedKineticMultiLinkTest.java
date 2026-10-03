package com.rieno.gadgetsandgizmos.lib.kinetics;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class LinkedKineticMultiLinkTest {
    @Test
    void removingOneBranchKeepsOtherReciprocalBranchAndAddsNoStress() throws Exception {
        Level level = mock(Level.class);
        when(level.isLoaded(org.mockito.ArgumentMatchers.any(BlockPos.class))).thenReturn(true);
        Endpoint hub = endpoint(level, BlockPos.ZERO);
        Endpoint first = endpoint(level, new BlockPos(4, 0, 0));
        Endpoint second = endpoint(level, new BlockPos(0, 0, 4));
        doReturn(List.of(first, second)).when(hub).resolveKineticLinks();
        doReturn(List.of(hub)).when(first).resolveKineticLinks();
        doReturn(List.of(hub)).when(second).resolveKineticLinks();

        hub.refreshKineticLink();
        assertEquals(1.0f, modifier(hub, first));
        assertEquals(1.0f, modifier(hub, second));
        assertEquals(1.0f, modifier(first, hub));
        assertEquals(1.0f, modifier(second, hub));
        assertEquals(0.0f, hub.calculateAddedStressCapacity());
        assertEquals(0.0f, hub.calculateStressApplied());

        doReturn(List.of(second)).when(hub).resolveKineticLinks();
        hub.refreshKineticLink();
        assertEquals(0.0f, modifier(first, hub));
        assertEquals(1.0f, modifier(hub, second));
        assertEquals(1.0f, modifier(second, hub));
    }

    @Test
    void parallelBeltBranchesDoNotMultiplyNetworkCapacityOrStress() throws Exception {
        Level level = mock(Level.class);
        when(level.isLoaded(org.mockito.ArgumentMatchers.any(BlockPos.class))).thenReturn(true);
        Endpoint hub = endpoint(level, BlockPos.ZERO);
        List<Endpoint> spokes = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            Endpoint spoke = endpoint(level, new BlockPos(index + 1, 0, 4));
            spokes.add(spoke);
        }
        for (int index = 0; index < spokes.size(); index++) {
            doReturn(List.of(hub, spokes.get((index + 1) % spokes.size()),
                    spokes.get((index + spokes.size() - 1) % spokes.size())))
                    .when(spokes.get(index)).resolveKineticLinks();
        }
        doReturn(spokes).when(hub).resolveKineticLinks();
        hub.refreshKineticLink();
        for (Endpoint spoke : spokes) spoke.refreshKineticLink();

        KineticBlockEntity generator = mock(KineticBlockEntity.class);
        when(generator.isSource()).thenReturn(true);
        when(generator.getGeneratedSpeed()).thenReturn(32.0f);
        when(generator.calculateAddedStressCapacity()).thenReturn(256.0f);
        when(generator.getLevel()).thenReturn(level);
        when(generator.getBlockPos()).thenReturn(new BlockPos(-1, 0, 0));
        KineticBlockEntity load = mock(KineticBlockEntity.class);
        when(load.getTheoreticalSpeed()).thenReturn(32.0f);
        when(load.calculateStressApplied()).thenReturn(64.0f);
        when(load.getLevel()).thenReturn(level);
        when(load.getBlockPos()).thenReturn(new BlockPos(5, 0, 0));
        when(level.getBlockEntity(generator.getBlockPos())).thenReturn(generator);
        when(level.getBlockEntity(load.getBlockPos())).thenReturn(load);
        when(level.getBlockEntity(hub.getBlockPos())).thenReturn(hub);
        for (Endpoint spoke : spokes) when(level.getBlockEntity(spoke.getBlockPos())).thenReturn(spoke);
        try (MockedStatic<IRotate.StressImpact> stress = mockStatic(IRotate.StressImpact.class)) {
            stress.when(IRotate.StressImpact::isEnabled).thenReturn(true);
            KineticNetwork network = new KineticNetwork();
            network.add(generator);
            network.add(load);
            for (int repeat = 0; repeat < 8; repeat++) {
                network.add(hub);
                for (Endpoint spoke : spokes) network.add(spoke);
                assertEquals(8192.0f, network.calculateCapacity());
                assertEquals(2048.0f, network.calculateStress());
            }
        }
    }

    private static Endpoint endpoint(Level level, BlockPos pos) throws Exception {
        Endpoint wheel = mock(Endpoint.class, CALLS_REAL_METHODS);
        Field links = LinkedKineticBlockEntity.class.getDeclaredField("connectedEndpoints");
        links.setAccessible(true);
        links.set(wheel, new LinkedHashSet<LinkedKineticBlockEntity>());
        wheel.setLevel(level);
        doReturn(pos).when(wheel).getBlockPos();
        doNothing().when(wheel).detachKinetics();
        doNothing().when(wheel).removeSource();
        doNothing().when(wheel).setChanged();
        doNothing().when(wheel).sendData();
        return wheel;
    }

    private abstract static class Endpoint extends LinkedKineticBlockEntity {
        private Endpoint() { super(null, BlockPos.ZERO, null); }

        @Override
        protected LinkedKineticBlockEntity resolveKineticLink() { return null; }
    }

    private static float modifier(KineticBlockEntity from, KineticBlockEntity to) {
        return from.propagateRotationTo(to, null, null,
                to.getBlockPos().subtract(from.getBlockPos()), false, false);
    }
}
