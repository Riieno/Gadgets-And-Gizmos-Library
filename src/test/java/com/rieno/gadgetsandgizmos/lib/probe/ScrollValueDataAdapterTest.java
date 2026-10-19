package com.rieno.gadgetsandgizmos.lib.probe;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScrollValueDataAdapterTest{
    @BeforeAll
    static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void numericWritesUseTheRealClampAndCallback(){
        var target = mock(SmartBlockEntity.class);
        when(target.getLevel()).thenReturn(mock(Level.class));
        AtomicInteger applied = new AtomicInteger();
        var speed = new ScrollValueBehaviour(Component.literal("Speed"), target, null)
                .between(-45, 45).withCallback(applied::set);
        var adapter = new ScrollValueDataAdapter<>(SmartBlockEntity.class, ignored -> Map.of("speed", speed));
        assertTrue(adapter.write(target, "speed", GraphValue.number(900)));
        assertEquals(45, applied.get());
        assertEquals(GraphValue.number(45), adapter.read(target, "speed"));
        assertTrue(adapter.write(target, "speed", GraphValue.number(-900)));
        assertEquals(-45, applied.get());
        verify(target, times(2)).setChanged();
        verify(target, times(2)).sendData();
        assertFalse(adapter.write(target, "speed", GraphValue.string("invalid")));
        assertFalse(adapter.write(target, "speed", new GraphValue("number", Double.NaN)));
        assertFalse(adapter.write(target, "unknown", GraphValue.number(1)));
    }

    @Test
    void enumPortsExposeOptionsAndRejectInvalidSelections(){
        var target = mock(SmartBlockEntity.class);
        when(target.getLevel()).thenReturn(mock(Level.class));
        var mode = new ScrollOptionBehaviour<>(Mode.class, Component.literal("Mode"), target, null);
        var adapter = new ScrollValueDataAdapter<>(SmartBlockEntity.class, ignored -> Map.of("mode", mode));
        assertEquals(List.of("forward", "reverse"), adapter.ports(target).getFirst().options());
        assertTrue(adapter.write(target, "mode", GraphValue.string("REVERSE")));
        assertEquals(GraphValue.string("reverse"), adapter.read(target, "mode"));
        assertFalse(adapter.write(target, "mode", GraphValue.string("missing")));
        assertEquals(Mode.REVERSE, mode.get());
    }

    @Test
    void supplementalSettingsPreserveTheTargetsPrimaryControls(){
        var target = mock(Fixture.class);
        target.targetSpeed = new ScrollValueBehaviour(Component.literal("Speed"), target, null);
        var adapter = new ScrollValueDataAdapter<>(Fixture.class, ScrollValueDataAdapter::fieldBehaviours);
        BlockEntityDataAdapterRegistry.register(0, adapter);
        assertEquals("number", BlockEntityDataAdapterRegistry.writableData(target).get("target_speed"));
        assertFalse(BlockEntityDataAdapterRegistry.hasPrimaryWritableData(target));
        BlockEntityDataAdapterRegistry.register(100, new BlockEntityDataAdapter<Fixture>(){
            public Class<Fixture> targetType(){ return Fixture.class; }
            public List<BlockEntityDataPort> ports(Fixture ignored){
                return List.of(BlockEntityDataPort.readWrite("enabled", "boolean"));
            }
        });
        assertTrue(BlockEntityDataAdapterRegistry.hasPrimaryWritableData(target));
    }

    @Test
    void discoveryExcludesScreenLaunchersAndItemMutationPorts(){
        var target = mock(Fixture.class);
        target.targetSpeed = new ScrollValueBehaviour(Component.literal("Speed"), target, null);
        target.screen = new ScrollOptionBehaviour<>(Screen.class, Component.literal("Configure"), target, null);
        assertEquals(List.of("target_speed"), List.copyOf(ScrollValueDataAdapter.fieldBehaviours(target).keySet()));
        var adapter = new ScrollValueDataAdapter<>(Fixture.class, ignored -> Map.of("item_count", target.targetSpeed));
        assertTrue(adapter.ports(target).isEmpty());
        assertFalse(adapter.write(target, "item_count", GraphValue.number(100)));
    }

    @Test
    void unavailableAndClientTargetsCannotBeMutated() throws ReflectiveOperationException{
        var target = mock(SmartBlockEntity.class);
        var speed = mock(ScrollValueBehaviour.class);
        var adapter = new ScrollValueDataAdapter<>(SmartBlockEntity.class, ignored -> Map.of("speed", speed));
        assertFalse(adapter.write(target, "speed", GraphValue.number(1)));
        when(target.getLevel()).thenReturn(mock(Level.class));
        when(target.isRemoved()).thenReturn(true);
        assertFalse(adapter.write(target, "speed", GraphValue.number(1)));
        when(target.isRemoved()).thenReturn(false);
        var client = mock(Level.class);
        var side = Level.class.getDeclaredField("isClientSide");
        side.setAccessible(true);
        side.setBoolean(client, true);
        when(target.getLevel()).thenReturn(client);
        assertFalse(adapter.write(target, "speed", GraphValue.number(1)));
        verify(speed, never()).setValue(anyInt());
    }

    enum Mode implements INamedIconOptions{
        FORWARD, REVERSE;
        public AllIcons getIcon(){ return null; }
        public String getTranslationKey(){ return name(); }
    }
    enum Screen implements INamedIconOptions{
        CONFIGURE;
        public AllIcons getIcon(){ return null; }
        public String getTranslationKey(){ return name(); }
    }

    abstract static class Fixture extends SmartBlockEntity{
        ScrollValueBehaviour targetSpeed;
        ScrollOptionBehaviour<Screen> screen;
        Fixture(BlockEntityType<?> type, BlockPos pos, BlockState state){ super(type, pos, state); }
        @Override
        public void addBehaviours(List<BlockEntityBehaviour> behaviours){}
    }
}
