package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuiRenderLayerDepthTest{
    @Test void inventoryIconsDecorationsAndCarriedItemsStayAboveTheirPanelBelowHigherModals(){
        GuiGraphics graphics = mock(GuiGraphics.class);PoseStack pose = new PoseStack();when(graphics.pose()).thenReturn(pose);
        try(var foreground = GuiRenderLayer.pushFlattened(graphics, 400)){
            for(float vanillaDepth : new float[]{10, 100, 250, 382, 550}){
                float depth = point(pose, vanillaDepth).z();
                assertTrue(depth > 400 && depth < 401, "Vanilla GUI depth escaped the foreground: " + depth);
            }
            try(var background = GuiRenderLayer.restoreBackground(graphics, 400)){
                assertEquals(0, point(pose, 0).z(), 0.0001);
                assertEquals(150, point(pose, 150).z(), 0.0001);
                try(var modal = GuiRenderLayer.push(graphics, 400)){
                    assertEquals(400, point(pose, 0).z(), 0.0001);
                }
            }
            assertEquals(400, point(pose, 0).z(), 0.0001);
        }
        assertEquals(0, point(pose, 0).z(), 0.0001);
        assertTrue(pose.clear());
    }

    @Test void backgroundAndForegroundRestoreAnExistingHostTransformAfterAnException(){
        GuiGraphics graphics = mock(GuiGraphics.class);PoseStack pose = new PoseStack();when(graphics.pose()).thenReturn(pose);
        pose.translate(7, 11, 20);pose.scale(2, 3, 1);
        Vector3f expected = new Vector3f(9, 14, 23);
        Matrix3f expectedNormal = new Matrix3f(pose.last().normal());
        assertThrows(IllegalStateException.class, () -> {
            try(var foreground = GuiRenderLayer.pushFlattened(graphics, 500)){
                assertEquals(expectedNormal, pose.last().normal());
                try(var background = GuiRenderLayer.restoreBackground(graphics, 500)){
                    assertEquals(expectedNormal, pose.last().normal());
                    assertTrue(expected.distance(pose.last().pose().transformPosition(new Vector3f(1, 1, 3))) < .0001F);
                    throw new IllegalStateException("render failed");
                }
            }
        });
        assertEquals(expected, pose.last().pose().transformPosition(new Vector3f(1, 1, 3)));
        assertTrue(pose.clear());
    }

    private static Vector3f point(PoseStack pose, float z){ return pose.last().pose().transformPosition(new Vector3f(0, 0, z)); }
}
