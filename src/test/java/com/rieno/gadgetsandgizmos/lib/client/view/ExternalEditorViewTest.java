package com.rieno.gadgetsandgizmos.lib.client.view;

import com.rieno.gadgetsandgizmos.lib.client.render.ClientViewOwnership;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewPayload;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewStatePayload;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.lang.reflect.Field;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalEditorViewTest{
    @BeforeAll static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.neoforged.fml.ModList.of(java.util.List.of(), java.util.List.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    @Test void remoteLensYieldsImmediatelyAndRestoresTheBodyPreferenceAfterEditorExit() throws Exception{
        Minecraft mc = mock(Minecraft.class);Options options = mock(Options.class);LocalPlayer player = mock(LocalPlayer.class);
        MouseHandler mouse = mock(MouseHandler.class);
        set(mc, "options", options);set(mc, "player", player);set(mc, "level", mock(ClientLevel.class));set(mc, "mouseHandler", mouse);
        var ref = new ViewReference(ResourceLocation.parse("minecraft:overworld"), null, BlockPos.ZERO);
        setStatic(RemoteViewClient.class, "source", ref);setStatic(RemoteViewClient.class, "prevCameraType", CameraType.THIRD_PERSON_BACK);
        try(var minecraft = mockStatic(Minecraft.class);var ownership = mockStatic(ClientViewOwnership.class);
            var keys = mockStatic(KeyMapping.class);var packets = mockStatic(PacketDistributor.class)){
            minecraft.when(Minecraft::getInstance).thenReturn(mc);ownership.when(ClientViewOwnership::isReserved).thenReturn(true);
            assertFalse(RemoteViewClient.isActive());assertNull(RemoteViewClient.pose(.5F));
            RemoteViewClient.tick(null);
            packets.verify(() -> PacketDistributor.sendToServer(new RemoteViewPayload(0, 0, 0, true)));
            assertNull(getStatic(RemoteViewClient.class, "source"));verifyNoInteractions(player, options);
            assertEquals(CameraType.THIRD_PERSON_BACK, getStatic(RemoteViewClient.class, "prevCameraType"));
            ownership.when(ClientViewOwnership::isReserved).thenReturn(false);RemoteViewClient.tick(null);
            verify(options).setCameraType(CameraType.THIRD_PERSON_BACK);assertNull(getStatic(RemoteViewClient.class, "prevCameraType"));
            packets.verifyNoMoreInteractions();
        }finally{setStatic(RemoteViewClient.class, "source", null);setStatic(RemoteViewClient.class, "prevCameraType", null);}
    }

    @Test void lateRemoteAcknowledgementCannotGrabTheEditorsMouse() throws Exception{
        Minecraft mc = mock(Minecraft.class);Options options = mock(Options.class);MouseHandler mouse = mock(MouseHandler.class);
        set(mc, "options", options);set(mc, "mouseHandler", mouse);set(mc, "player", mock(LocalPlayer.class));set(mc, "level", mock(ClientLevel.class));
        var ref = new ViewReference(ResourceLocation.parse("minecraft:overworld"), null, BlockPos.ZERO);
        try(var minecraft = mockStatic(Minecraft.class);var ownership = mockStatic(ClientViewOwnership.class);
            var keys = mockStatic(KeyMapping.class);var packets = mockStatic(PacketDistributor.class)){
            minecraft.when(Minecraft::getInstance).thenReturn(mc);ownership.when(ClientViewOwnership::isReserved).thenReturn(true);
            var handle = RemoteViewClient.class.getDeclaredMethod("handle", RemoteViewStatePayload.class);handle.setAccessible(true);
            handle.invoke(null, new RemoteViewStatePayload(ref.toTag(), true));
            assertFalse(RemoteViewClient.isActive());verify(mouse, never()).grabMouse();verifyNoInteractions(options);
            packets.verify(() -> PacketDistributor.sendToServer(new RemoteViewPayload(0, 0, 0, true)));
        }finally{setStatic(RemoteViewClient.class, "source", null);setStatic(RemoteViewClient.class, "prevCameraType", null);}
    }

    @Test void reservedSceneRequestsRetainTheLastTextureWithoutResolvingOrAllocatingARenderer() throws Exception{
        Minecraft mc = mock(Minecraft.class);set(mc, "level", mock(ClientLevel.class));
        ViewReference ref = new ViewReference(ResourceLocation.parse("minecraft:overworld"), null, BlockPos.ZERO);var texture = ResourceLocation.parse("ownership_test:last_frame");
        Class<?> type = Class.forName(ViewSceneRenderer.class.getName() + "$Capture");Object capture = mock(type);
        set(capture, "texture", texture);set(capture, "ready", true);
        Map<ViewReference,Object> captures = (Map<ViewReference,Object>)getStatic(ViewSceneRenderer.class, "CAPTURES");
        Object previousLevel = getStatic(ViewSceneRenderer.class, "owningLevel");setStatic(ViewSceneRenderer.class, "owningLevel", mc.level);
        captures.put(ref, capture);
        try(var minecraft = mockStatic(Minecraft.class);var ownership = mockStatic(ClientViewOwnership.class)){
            minecraft.when(Minecraft::getInstance).thenReturn(mc);ownership.when(ClientViewOwnership::isReserved).thenReturn(true);
            assertEquals(texture, ViewSceneRenderer.request(ref, 640, 360));
            assertNull(ViewSceneRenderer.request(new ViewReference(ResourceLocation.parse("minecraft:overworld"), null, new BlockPos(1, 0, 0)), 640, 360));
            ViewSceneRenderer.render(mock(RenderFrameEvent.Pre.class));
            assertSame(capture, captures.get(ref));assertEquals(1, captures.size());
            verify(mc.level, never()).dimension();verify(mc, never()).getMainRenderTarget();
        }finally{captures.remove(ref);setStatic(ViewSceneRenderer.class, "owningLevel", previousLevel);}
    }

    private static Object getStatic(Class<?> type, String name) throws Exception{Field f = type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static void setStatic(Class<?> type, String name, Object value) throws Exception{Field f = type.getDeclaredField(name);f.setAccessible(true);f.set(null, value);}
    private static void set(Object target, String name, Object value) throws Exception{
        Field f = target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target, value);
    }
}
