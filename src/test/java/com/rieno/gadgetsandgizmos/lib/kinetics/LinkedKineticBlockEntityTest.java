package com.rieno.gadgetsandgizmos.lib.kinetics;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.TorquePropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CServer;
import net.createmod.catnip.config.ConfigBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LinkedKineticBlockEntityTest {
    private MockedStatic<IRotate.StressImpact> stressConfig;

    @org.junit.jupiter.api.BeforeAll
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

    @BeforeEach
    void enableStress(){
        stressConfig = mockStatic(IRotate.StressImpact.class);
        stressConfig.when(IRotate.StressImpact::isEnabled).thenReturn(true);
    }

    @AfterEach
    void closeConfig(){
        stressConfig.close();
    }

    @Test
    void stackedLinksDoNotGenerateCapacityOrDuplicateLoads(){
        Level level = mock(Level.class);
        KineticNetwork network = new KineticNetwork();
        KineticBlockEntity generator = mock(KineticBlockEntity.class);
        when(generator.isSource()).thenReturn(true);
        when(generator.getGeneratedSpeed()).thenReturn(32f);
        when(generator.calculateAddedStressCapacity()).thenReturn(256f);
        register(level, generator, new BlockPos(0, 0, 0));
        network.add(generator);
        KineticBlockEntity load = mock(KineticBlockEntity.class);
        when(load.getTheoreticalSpeed()).thenReturn(32f);
        when(load.calculateStressApplied()).thenReturn(64f);
        register(level, load, new BlockPos(1, 0, 0));
        network.add(load);

        for(int pair = 0; pair < 128; pair++){
            LinkedKineticBlockEntity first = endpoint(level, new BlockPos(pair + 2, 0, 0));
            LinkedKineticBlockEntity second = endpoint(level, new BlockPos(pair + 2, 0, 20));
            when(first.resolveKineticLink()).thenReturn(second);
            when(second.resolveKineticLink()).thenReturn(first);
            first.refreshKineticLink();
            for(LinkedKineticBlockEntity wheel : new LinkedKineticBlockEntity[]{first, second}){
                network.add(wheel);
                network.add(wheel);
                assertFalse(wheel.isSource());
                assertEquals(0f, wheel.calculateAddedStressCapacity());
                assertEquals(0f, wheel.calculateStressApplied());
            }
            assertEquals(1f, modifier(first, second));
            assertEquals(1f, modifier(second, first));
            assertEquals(8192f, network.calculateCapacity());
            assertEquals(2048f, network.calculateStress());
        }
        network.updateStressFor(load, 512f);
        assertEquals(16384f, network.calculateStress());
        assertTrue(network.calculateStress() > network.calculateCapacity());
        network.updateStressFor(load, 0f);
        assertEquals(0f, network.calculateStress());
        assertEquals(1, network.sources.size());
    }

    @Test
    void linksRequireReciprocityAndDetachWithOldEdgeStillVisible(){
        Level level = mock(Level.class);
        LinkedKineticBlockEntity first = endpoint(level, BlockPos.ZERO);
        LinkedKineticBlockEntity second = endpoint(level, new BlockPos(0, 0, 20));
        when(first.resolveKineticLink()).thenReturn(second);
        first.refreshKineticLink();
        assertEquals(0f, modifier(first, second));
        when(second.resolveKineticLink()).thenReturn(first);
        first.refreshKineticLink();
        assertEquals(1f, modifier(first, second));
        doAnswer(call -> {
            assertEquals(1f, modifier(first, second));
            return null;
        }).when(first).detachKinetics();
        when(first.resolveKineticLink()).thenReturn(null);
        first.refreshKineticLink();
        assertEquals(0f, modifier(first, second));
        assertEquals(0f, modifier(second, first));
        assertTrue(first.updateSpeed);
        assertTrue(second.updateSpeed);
    }

    @Test
    void remotePositionIsAddedOnlyOnceAndMissingEndpointStopsTransmission(){
        Level level = mock(Level.class);
        LinkedKineticBlockEntity first = endpoint(level, BlockPos.ZERO);
        LinkedKineticBlockEntity second = endpoint(level, new BlockPos(0, 0, 20));
        when(first.resolveKineticLink()).thenReturn(second);
        when(second.resolveKineticLink()).thenReturn(first);
        first.refreshKineticLink();
        ArrayList<BlockPos> positions = new ArrayList<>();
        positions.add(second.getBlockPos());
        assertEquals(positions, first.addPropagationLocations(null, null, positions));
        assertEquals(1, positions.size());
        when(second.isRemoved()).thenReturn(true);
        first.refreshKineticLink();
        assertEquals(0f, modifier(first, second));
        assertEquals(0f, first.getGeneratedSpeed());
    }

    private static LinkedKineticBlockEntity endpoint(Level level, BlockPos pos){
        LinkedKineticBlockEntity wheel = mock(Endpoint.class, CALLS_REAL_METHODS);
        // Keep game notifications outside this network/lifecycle regression fixture.
        doNothing().when(wheel).detachKinetics();
        doNothing().when(wheel).removeSource();
        doNothing().when(wheel).setChanged();
        doNothing().when(wheel).sendData();
        doNothing().when(wheel).onSpeedChanged(anyFloat());
        wheel.setLevel(level);
        register(level, wheel, pos);
        return wheel;
    }

    private static void register(Level level, KineticBlockEntity member, BlockPos pos){
        try {
            var position = net.minecraft.world.level.block.entity.BlockEntity.class.getDeclaredField("worldPosition");
            position.setAccessible(true);
            position.set(member, pos);
        } catch(ReflectiveOperationException failure){
            throw new AssertionError(failure);
        }
        doReturn(level).when(member).getLevel();
        doReturn(pos).when(member).getBlockPos();
        when(level.getBlockEntity(pos)).thenReturn(member);
        when(level.isLoaded(pos)).thenReturn(true);
        when(level.tickRateManager()).thenReturn(mock(net.minecraft.world.TickRateManager.class));
    }

    private abstract static class Endpoint extends LinkedKineticBlockEntity {
        private Endpoint(){ super(null, BlockPos.ZERO, null); }

        @Override
        protected boolean canPropagateDiagonally(com.simibubi.create.content.kinetics.base.IRotate block,
                                                 net.minecraft.world.level.block.state.BlockState state){
            return false;
        }
    }

    private static float modifier(LinkedKineticBlockEntity from, LinkedKineticBlockEntity to){
        return from.propagateRotationTo(to, null, null, to.getBlockPos().subtract(from.getBlockPos()), false, false);
    }

    @Test
    void legacyTotalsRebuildBeforeDetachingAndOnlyOnce(){
        Level level = mock(Level.class);
        LinkedKineticBlockEntity wheel = endpoint(level, BlockPos.ZERO);
        KineticNetwork network = new KineticNetwork();
        wheel.network = 123L;
        doReturn(network).when(wheel).getOrCreateNetwork();
        KineticBlockEntity generator = mock(KineticBlockEntity.class);
        register(level, generator, new BlockPos(0, 0, -1));
        when(generator.isSource()).thenReturn(true);
        when(generator.getGeneratedSpeed()).thenReturn(32f);
        when(generator.calculateAddedStressCapacity()).thenReturn(256f);
        network.add(generator);
        network.sources.put(wheel, Float.POSITIVE_INFINITY);
        network.members.put(wheel, Float.NaN);
        network.initFromTE(Float.POSITIVE_INFINITY, Float.NaN, 42);
        wheel.rebuildKineticNetworkOnLoad();
        doAnswer(call -> {
            assertEquals(8192f, network.calculateCapacity());
            assertEquals(0f, network.calculateStress());
            return null;
        }).when(wheel).detachKinetics();
        wheel.refreshKineticLink();
        assertEquals(1, network.sources.size());
        assertFalse(network.sources.containsKey(wheel));
        network.initFromTE(1024, 512, 2);
        wheel.refreshKineticLink();
        assertEquals(9216f, network.calculateCapacity());
        assertEquals(512f, network.calculateStress());
    }

    @Test
    void rePairingDisconnectsBothFormerPartners(){
        Level level = mock(Level.class);
        LinkedKineticBlockEntity a = endpoint(level, BlockPos.ZERO);
        LinkedKineticBlockEntity b = endpoint(level, new BlockPos(20, 0, 0));
        LinkedKineticBlockEntity c = endpoint(level, new BlockPos(40, 0, 0));
        LinkedKineticBlockEntity d = endpoint(level, new BlockPos(60, 0, 0));
        when(a.resolveKineticLink()).thenReturn(b);
        when(b.resolveKineticLink()).thenReturn(a);
        when(c.resolveKineticLink()).thenReturn(d);
        when(d.resolveKineticLink()).thenReturn(c);
        a.refreshKineticLink();
        c.refreshKineticLink();
        when(a.resolveKineticLink()).thenReturn(c);
        when(c.resolveKineticLink()).thenReturn(a);
        a.refreshKineticLink();
        assertEquals(1f, modifier(a, c));
        assertEquals(1f, modifier(c, a));
        assertEquals(0f, modifier(b, a));
        assertEquals(0f, modifier(d, c));
        when(level.isLoaded(c.getBlockPos())).thenReturn(false);
        a.refreshKineticLink();
        assertEquals(0f, modifier(a, c));
    }

    @Test
    void createPropagatesStackedAndSerialLinksWithOverstressAndDisconnection() throws Exception {
        CServer server = new CServer();
        ConfigBase.ConfigInt maxSpeed = mock(ConfigBase.ConfigInt.class);
        when(maxSpeed.get()).thenReturn(256);
        var field = server.kinetics.getClass().getField("maxRotationSpeed");
        field.setAccessible(true);
        field.set(server.kinetics, maxSpeed);
        try(MockedStatic<AllConfigs> configs = mockStatic(AllConfigs.class)){
            configs.when(AllConfigs::server).thenReturn(server);
            for(float rpm : new float[]{32, -32}){
                for(boolean serial : new boolean[]{false, true}){
                    Level level = mock(Level.class);
                    when(level.getBlockState(any())).thenReturn(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    TorquePropagator torque = new TorquePropagator();
                    Block cog = mock(Block.class, withSettings().extraInterfaces(IRotate.class, ICogWheel.class));
                    when(((ICogWheel) cog).isSmallCog()).thenReturn(true);
                    IRotate rotate = (IRotate) cog;
                    when(rotate.getRotationAxis(any())).thenReturn(Direction.Axis.Z);
                    when(rotate.hasShaftTowards(any(), any(), any(), any())).thenAnswer(call ->
                            ((Direction) call.getArgument(3)).getAxis() == Direction.Axis.Z);
                    BlockState state = mock(BlockState.class);
                    when(state.getBlock()).thenReturn(cog);
                    when(state.hasBlockEntity()).thenReturn(true);

                    List<LinkedKineticBlockEntity> wheels = new ArrayList<>();
                    for(int row = 0; row < 12; row++){
                        LinkedKineticBlockEntity left = endpoint(level, serial
                                ? new BlockPos(20 * row, 0, row) : new BlockPos(0, row, 0));
                        LinkedKineticBlockEntity right = endpoint(level, serial
                                ? new BlockPos(20 * (row + 1), 0, row) : new BlockPos(20, row, 0));
                        for(LinkedKineticBlockEntity wheel : new LinkedKineticBlockEntity[]{left, right}){
                            configureKinetics(level, torque, state, wheel);
                            doCallRealMethod().when(wheel).detachKinetics();
                            doCallRealMethod().when(wheel).removeSource();
                            wheels.add(wheel);
                      }
                    when(left.resolveKineticLink()).thenReturn(right);
                    when(right.resolveKineticLink()).thenReturn(left);
                    left.refreshKineticLink();
                }

                KineticBlockEntity generator = mock(KineticBlockEntity.class, CALLS_REAL_METHODS);
                register(level, generator, new BlockPos(0, 0, -1));
                configureKinetics(level, torque, state, generator);
                doReturn(rpm).when(generator).getGeneratedSpeed();
                doReturn(256f).when(generator).calculateAddedStressCapacity();
                doReturn(0f).when(generator).calculateStressApplied();
                generator.setSpeed(rpm);
                generator.setNetwork(123L);
                generator.attachKinetics();

                KineticBlockEntity load = mock(KineticBlockEntity.class, CALLS_REAL_METHODS);
                register(level, load, serial ? new BlockPos(240, 0, 12) : new BlockPos(20, 0, -1));
                configureKinetics(level, torque, state, load);
                doReturn(64f).when(load).calculateStressApplied();
                load.attachKinetics();
                KineticNetwork network = generator.getOrCreateNetwork();
                network.updateNetwork();
                assertEquals(rpm, load.getSpeed());
                assertEquals(8192f, network.calculateCapacity());
                assertEquals(2048f, network.calculateStress());
                for(LinkedKineticBlockEntity wheel : wheels){
                    assertEquals(generator.network, wheel.network);
                    assertEquals(Math.abs(rpm), Math.abs(wheel.getSpeed()));
                    assertFalse(wheel.isSource());
                }
                assertEquals(1, network.sources.size());
                network.updateStressFor(load, 512f);
                assertTrue(load.isOverStressed());
                assertEquals(0f, load.getSpeed());
                assertTrue(wheels.stream().allMatch(KineticBlockEntity::isOverStressed));
                network.updateStressFor(load, 0f);
                assertEquals(rpm, load.getSpeed());

                // Remove every spline; no remote generator may keep the load rotating.
                for(LinkedKineticBlockEntity wheel : wheels){
                    when(wheel.resolveKineticLink()).thenReturn(null);
                    wheel.refreshKineticLink();
                }
                for(LinkedKineticBlockEntity wheel : wheels) wheel.attachKinetics();
                assertEquals(0f, load.getSpeed());
                assertFalse(load.hasNetwork());
                verify(level, never()).destroyBlock(any(), anyBoolean());
              }
            }
        }
    }

    private static void configureKinetics(Level level, TorquePropagator torque, BlockState state,
                                          KineticBlockEntity member){
        member.setLevel(level);
        doReturn(state).when(member).getBlockState();
        when(level.getBlockState(member.getBlockPos())).thenReturn(state);
        doAnswer(call -> torque.getOrCreateNetworkFor(member)).when(member).getOrCreateNetwork();
        doNothing().when(member).setChanged();
        doNothing().when(member).sendData();
        doNothing().when(member).onSpeedChanged(anyFloat());
    }
}
