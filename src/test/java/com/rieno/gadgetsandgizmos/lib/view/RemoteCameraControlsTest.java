package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewControlLayout;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewControlPanel;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteCameraControlsTest{
    @BeforeAll static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }

    @Test void takeoverHasItsOwnPointerRegionAndInvokesOnlyItsHost() throws ReflectiveOperationException{
        for(int[] size : new int[][]{{264, 168}, {320, 180}, {800, 450}, {1920, 1080}}){
            var layout = ViewControlLayout.overlay(17, 23, size[0], size[1]);
            var button = layout.takeControl();
            assertTrue(button.y() + button.height() < layout.stickY() - layout.radius());
            assertTrue(button.x() >= 17 && button.x() + button.width() <= 17 + size[0]);
            for(var other : List.of(layout.flashlight(), layout.tracking(), layout.targetLock(), layout.zoom())){
                assertFalse(other.contains(button.centerX(), button.centerY()));
            }
            assertFalse(layout.modes().stream().anyMatch(other -> other.contains(button.centerX(), button.centerY())));
            var panel = new ViewControlPanel();
            var field = ViewControlPanel.class.getDeclaredField("layout"); field.setAccessible(true); field.set(panel, layout);
            var called = new AtomicInteger();
            panel.setTakeControlAction(called::incrementAndGet);
            assertTrue(panel.mouseClicked(button.centerX(), button.centerY(), 0));
            assertEquals(1, called.get());
            assertTrue(panel.mouseClicked(button.centerX(), button.centerY(), 1));
            assertEquals(2, called.get());
        }
    }

    @Test void overlayFrameGridAndUseButtonsHaveIndependentHitRegions() throws ReflectiveOperationException{
        var layout = ViewControlLayout.overlay(17, 23, 264, 168);
        var buttons = List.of(layout.flashlight(), layout.tracking(), layout.targetLock(), layout.takeControl(),
                layout.frame(), layout.grid(), layout.interact());
        for(var button : buttons){
            assertTrue(button.x() >= 17 && button.x() + button.width() <= 281);
            for(var other : buttons) if(other != button) assertFalse(other.contains(button.centerX(), button.centerY()));
        }
        var panel = new ViewControlPanel();
        var field = ViewControlPanel.class.getDeclaredField("layout"); field.setAccessible(true); field.set(panel, layout);
        var called = new AtomicInteger(); panel.setInteractAction(called::incrementAndGet);
        try(var settings = mockStatic(com.rieno.gadgetsandgizmos.lib.config.ViewfinderConfig.class)){
            assertTrue(panel.mouseClicked(layout.frame().centerX(), layout.frame().centerY(), 0));
            settings.verify(com.rieno.gadgetsandgizmos.lib.config.ViewfinderConfig::toggleFrame);
            assertTrue(panel.mouseClicked(layout.grid().centerX(), layout.grid().centerY(), 1));
            settings.verify(com.rieno.gadgetsandgizmos.lib.config.ViewfinderConfig::toggleGrid);
            assertEquals(0, called.get());
            assertTrue(panel.mouseClicked(layout.interact().centerX(), layout.interact().centerY(), 0));
            assertEquals(1, called.get());
        }
    }

    @Test void discreteActionsRequireOwnershipPreserveSettingsAndHaveIndependentTickLimits(){
        var level = mock(ServerLevel.class);
        var time = new AtomicLong(100);
        when(level.getGameTime()).thenAnswer(call -> time.get());
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION, dimension));
        var player = player(level);
        var other = player(level);
        var source = mock(BlockEntity.class, withSettings().extraInterfaces(ControlledViewSource.class, RaycastViewSource.class));
        var controls = (ControlledViewSource)source;
        var state = new AtomicReference<>(new ViewControlState(ViewRig.Mode.SENTRY, 43, false, true, true));
        when(controls.viewControlState()).thenAnswer(call -> state.get());
        when(controls.isViewAvailable()).thenReturn(true);
        doAnswer(call -> { state.set(call.getArgument(0)); return null; }).when(controls).applyViewSettings(any());
        var ray = ViewRaycast.Result.miss(new Vec3(0, 0, 32), 32);
        when(((RaycastViewSource)source).castViewRay()).thenReturn(ray);
        var ref = new ViewReference(dimension, null, BlockPos.ZERO);
        when(level.isLoaded(BlockPos.ZERO)).thenReturn(true);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(source);
        try(var packets = mockStatic(PacketDistributor.class)){
            assertFalse(RemoteViewSessions.toggleFlashlight(other));
            assertNull(RemoteViewSessions.raycast(other));
            assertTrue(RemoteViewSessions.open(player, ref));
            assertFalse(RemoteViewSessions.open(other, ref));
            assertTrue(RemoteViewSessions.toggleFlashlight(player));
            assertEquals(new ViewControlState(ViewRig.Mode.SENTRY, 43, true, true, true), state.get());
            assertFalse(RemoteViewSessions.toggleFlashlight(player));
            assertSame(ray, RemoteViewSessions.raycast(player));
            assertNull(RemoteViewSessions.raycast(player));
            time.incrementAndGet();
            assertTrue(RemoteViewSessions.toggleFlashlight(player));
            assertFalse(state.get().flashlight());
            verify((RaycastViewSource)source, times(1)).castViewRay();
            RemoteViewSessions.close(player);
            assertFalse(RemoteViewSessions.isLocked(player));
            assertFalse(RemoteViewSessions.toggleFlashlight(player));
            assertNull(RemoteViewSessions.raycast(player));
        }
    }

    @Test void unavailableActionsDoNotCloseReadOnlyEntityViews(){
        var level = mock(ServerLevel.class);
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION, dimension));
        var id = UUID.randomUUID();
        var entity = mock(net.minecraft.world.entity.Entity.class);
        when(entity.isAlive()).thenReturn(true);
        when(level.getEntity(id)).thenReturn(entity);
        var player = player(level);
        var ref = new ViewReference(dimension, null, BlockPos.ZERO, id);
        try(var packets = mockStatic(PacketDistributor.class)){
            assertTrue(RemoteViewSessions.open(player, ref));
            assertFalse(RemoteViewSessions.toggleFlashlight(player));
            assertNull(RemoteViewSessions.raycast(player));
            assertTrue(RemoteViewSessions.isLocked(player));
            RemoteViewSessions.close(player);
        }
    }

    @Test void configuredInteractionReceivesBothPressAndRelease(){
        var minecraft = mock(net.minecraft.client.Minecraft.class);
        var keys = com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewKeys.INTERACT.mapping();
        var prev = keys.getKey();
        try(var client = mockStatic(com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient.class);
            var mc = mockStatic(net.minecraft.client.Minecraft.class);
            var use = mockStatic(com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient.class)){
            mc.when(net.minecraft.client.Minecraft::getInstance).thenReturn(minecraft);
            client.when(com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient::isActive).thenReturn(true);
            keys.setKey(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(2));
            assertTrue(com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewKeys.mouse(2, 1));
            use.verify(com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient::press);
            assertTrue(com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewKeys.mouse(2, 0));
            use.verify(com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient::release);
        }finally{ keys.setKey(prev); }
    }

    @Test void feedButtonsFormALeftColumnAndTabletChinKeepsEveryControlBelowTheFeed(){
        for(int[] size : new int[][]{{264,168},{320,180},{800,450},{1920,1080}}){
            var overlay = ViewControlLayout.overlay(17,23,size[0],size[1]);
            var buttons = List.of(overlay.flashlight(), overlay.tracking(), overlay.targetLock(), overlay.takeControl(), overlay.frame(), overlay.grid(), overlay.interact());
            int prevBottom = 23;
            for(var button : buttons){
                assertEquals(buttons.getFirst().x(), button.x());
                assertTrue(button.y() >= prevBottom);
                prevBottom = button.y() + button.height();
            }
            assertTrue(prevBottom < overlay.stickY() - overlay.radius());
            var chin = ViewControlLayout.chin(17,300,size[0],76);
            var regions = new java.util.ArrayList<>(List.of(chin.flashlight(), chin.tracking(), chin.targetLock(), chin.takeControl(), chin.frame(), chin.grid(), chin.interact(), chin.zoom()));
            regions.addAll(chin.modes());
            for(var region : regions){
                assertTrue(region.x() >= 17 && region.x() + region.width() <= 17 + size[0]);
                assertTrue(region.y() >= 300 && region.y() + region.height() <= 376);
                for(var other : regions) if(region != other) assertFalse(other.contains(region.centerX(),region.centerY()));
            }
            assertTrue(chin.zoom().width() > chin.zoom().height());
        }
    }

    private static ServerPlayer player(ServerLevel level){
        var player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID()); when(player.level()).thenReturn(level);
        when(player.isAlive()).thenReturn(true); when(player.position()).thenReturn(Vec3.ZERO);
        player.connection = mock(ServerGamePacketListenerImpl.class);
        return player;
    }
}
