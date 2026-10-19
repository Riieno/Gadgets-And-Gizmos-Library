package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientViewOwnershipTest{
    @BeforeAll static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.neoforged.fml.ModList.of(java.util.List.of(), java.util.List.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    @Test void liveOwnersYieldFramesAndOverlaysThenReleaseNormally(){
        var id = ResourceLocation.parse("ownership_test:editor");
        var frameId = ResourceLocation.parse("ownership_test:frame");
        var overlayId = ResourceLocation.parse("ownership_test:overlay");
        var reserved = new AtomicBoolean();var frames = new AtomicInteger();var overlays = new AtomicInteger();
        Entity entity = mock(Entity.class);
        ClientViewOwnership.register(id, reserved::get);
        SubLevelCameraFrameRegistry.register(frameId, (camera, partial) -> {frames.incrementAndGet();return new Quaterniond();});
        PhysicsGogglesOverlayRegistry.register(overlayId, context -> overlays.incrementAndGet());
        try{
            assertFalse(ClientViewOwnership.isReserved());
            assertThrows(IllegalArgumentException.class, () -> ClientViewOwnership.register(id, () -> true));
            assertNotNull(SubLevelCameraFrameRegistry.orientation(entity, .5F));PhysicsGogglesOverlayRegistry.render(null);
            assertEquals(1, frames.get());assertEquals(1, overlays.get());
            reserved.set(true);assertTrue(ClientViewOwnership.isReserved());
            assertNull(SubLevelCameraFrameRegistry.orientation(entity, .5F));PhysicsGogglesOverlayRegistry.render(null);
            assertEquals(1, frames.get());assertEquals(1, overlays.get());
            reserved.set(false);
            assertNotNull(SubLevelCameraFrameRegistry.orientation(entity, .5F));PhysicsGogglesOverlayRegistry.render(null);
            assertEquals(2, frames.get());assertEquals(2, overlays.get());
            ClientViewOwnership.unregister(id);assertFalse(ClientViewOwnership.isReserved());
        }finally{
            ClientViewOwnership.unregister(id);SubLevelCameraFrameRegistry.unregister(frameId);PhysicsGogglesOverlayRegistry.unregister(overlayId);
        }
    }

    @Test void optionalAxiomProbeReadsTheLivePublicFlag(){
        Editor.enabled = false;
        var active = AxiomEditorCompat.probe(Editor.class);
        assertFalse(active.getAsBoolean());Editor.enabled = true;assertTrue(active.getAsBoolean());
        Editor.enabled = false;assertFalse(active.getAsBoolean());
        assertFalse(AxiomEditorCompat.isActive());
    }

    @Test void missingOrBrokenOptionalApisDoNotDisableNormalCameraUseOrRetryEveryFrame(){
        assertFalse(AxiomEditorCompat.probe(Object.class).getAsBoolean());
        assertFalse(AxiomEditorCompat.probe(WrongType.class).getAsBoolean());
        Broken.calls = 0;var active = AxiomEditorCompat.probe(Broken.class);
        for(int frame = 0; frame < 100; frame++)assertFalse(active.getAsBoolean());
        assertEquals(1, Broken.calls);
    }

    public static class Editor{static boolean enabled;public static boolean isEnabled(){return enabled;}}
    public static class WrongType{public static String isEnabled(){return "true";}}
    public static class Broken{static int calls;public static boolean isEnabled(){calls++;throw new LinkageError("optional API unavailable");}}
}
