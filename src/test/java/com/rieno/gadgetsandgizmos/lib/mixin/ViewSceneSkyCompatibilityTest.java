package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ViewSceneSkyCompatibilityTest{
    @Test void normalAndThirdPartySkyPositionsPassThroughOutsideOurCapture() throws Exception{
        try(var scenes = mockStatic(ViewSceneRenderer.class)){
            Renderer renderer = new Renderer();
            Vec3 player = new Vec3(12, 48, -6);
            Vec3 otherCapture = new Vec3(0, Double.POSITIVE_INFINITY, 0);
            assertSame(player, modify(renderer, player));
            assertSame(otherCapture, modify(renderer, otherCapture));
        }
    }

    @Test void ourUndergroundLensStillClearsTheHorizon() throws Exception{
        try(var scenes = mockStatic(ViewSceneRenderer.class)){
            scenes.when(ViewSceneRenderer::capturePose).thenReturn(new ViewPose(new Vec3(12, 10, -6), new Quaterniond(), 70));
            Renderer renderer = rendererAtHorizon(63);
            assertEquals(new Vec3(12, 64, -6), modify(renderer, new Vec3(0, Double.POSITIVE_INFINITY, 0)));
        }
    }

    @Test void ourAboveGroundLensRetainsItsPosition() throws Exception{
        try(var scenes = mockStatic(ViewSceneRenderer.class)){
            Vec3 lens = new Vec3(12, 90, -6);
            scenes.when(ViewSceneRenderer::capturePose).thenReturn(new ViewPose(lens, new Quaterniond(), 70));
            assertEquals(lens, modify(rendererAtHorizon(63), new Vec3(0, 10, 0)));
        }
    }

    private static Renderer rendererAtHorizon(double horizon) throws Exception{
        ClientLevel level = mock(ClientLevel.class);
        ClientLevel.ClientLevelData data = mock(ClientLevel.ClientLevelData.class);
        when(level.getLevelData()).thenReturn(data);
        when(data.getHorizonHeight(level)).thenReturn(horizon);
        Renderer renderer = new Renderer();
        var field = ViewSceneLevelRendererMixin.class.getDeclaredField("level");
        field.setAccessible(true); field.set(renderer, level);
        return renderer;
    }

    private static Vec3 modify(Renderer renderer, Vec3 original) throws Exception{
        var method = ViewSceneLevelRendererMixin.class.getDeclaredMethod("captureHorizonPosition", Vec3.class);
        method.setAccessible(true);
        return (Vec3)method.invoke(renderer, original);
    }

    private static final class Renderer extends ViewSceneLevelRendererMixin{
        @Override public void setLevel(ClientLevel level){}
        @Override public void close(){}
    }
}

