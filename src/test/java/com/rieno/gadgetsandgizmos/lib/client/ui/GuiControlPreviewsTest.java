package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuiControlPreviewsTest{
    @BeforeAll static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void slotAndItemUseTheSameZoomAndRetainTheParentPose(){
        for(double zoom : new double[]{0.55, 1, 1.75}){
            PoseStack pose = new PoseStack();
            pose.translate(10, 20, 0);
            Matrix4f parent = new Matrix4f(pose.last().pose());
            GuiGraphics graphics = mock(GuiGraphics.class);
            ItemStack stack = new ItemStack(Items.STICK);
            when(graphics.pose()).thenReturn(pose);
            List<Vector3f> slot = new ArrayList<>();
            List<Vector3f> item = new ArrayList<>();
            doAnswer(call -> {
                slot.add(point(pose, call.getArgument(0), call.getArgument(1)));
                slot.add(point(pose, call.getArgument(2), call.getArgument(3)));
                return null;
            }).when(graphics).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            doAnswer(call -> {
                int x = call.getArgument(1), y = call.getArgument(2);
                item.add(point(pose, x, y));
                item.add(point(pose, x + 16, y + 16));
                return null;
            }).when(graphics).renderItem(same(stack), anyInt(), anyInt());

            GuiControlPreviews.drawItemSlot(graphics, stack, 100, 50, 0xFFE45B67, true, zoom);
            assertPoint(slot.getFirst(), 110, 70);
            assertPoint(slot.getLast(), 110 + 18 * zoom, 70 + 18 * zoom);
            assertPoint(item.getFirst(), 110 + zoom, 70 + zoom);
            assertPoint(item.getLast(), 110 + 17 * zoom, 70 + 17 * zoom);
            assertEquals(parent, pose.last().pose());
        }
    }

    @Test void colorBorderAndInsetScaleTogether(){
        PoseStack pose = new PoseStack();
        GuiGraphics graphics = mock(GuiGraphics.class);
        when(graphics.pose()).thenReturn(pose);
        List<Vector3f> corners = new ArrayList<>();
        doAnswer(call -> {
            corners.add(point(pose, call.getArgument(0), call.getArgument(1)));
            corners.add(point(pose, call.getArgument(2), call.getArgument(3)));
            return null;
        }).when(graphics).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        GuiControlPreviews.drawColorSwatch(graphics, 100, 50, 0xFFAA3366, 0.5);
        assertPoint(corners.get(0), 100, 50.5);
        assertPoint(corners.get(1), 107, 56.5);
        assertPoint(corners.get(2), 101, 51.5);
        assertPoint(corners.get(3), 106, 55.5);
        assertEquals(new Matrix4f(), pose.last().pose());
    }

    @Test void itemFailureCannotLeakItsTransformIntoFollowingControls(){
        PoseStack pose = new PoseStack();
        pose.translate(15, 25, 0);
        Matrix4f parent = new Matrix4f(pose.last().pose());
        GuiGraphics graphics = mock(GuiGraphics.class);
        ItemStack stack = new ItemStack(Items.STICK);
        when(graphics.pose()).thenReturn(pose);
        doThrow(new IllegalStateException("item rendering failed")).when(graphics).renderItem(same(stack), anyInt(), anyInt());
        assertThrows(IllegalStateException.class,
                () -> GuiControlPreviews.drawItemSlot(graphics, stack, 100, 50, -1, true, 0.75));
        assertEquals(parent, pose.last().pose());
    }

    @Test void invalidZoomDoesNotDrawOrChangeThePose(){
        GuiGraphics graphics = mock(GuiGraphics.class);
        for(double zoom : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}){
            GuiControlPreviews.drawItemSlot(graphics, null, 0, 0, -1, true, zoom);
            GuiControlPreviews.drawColorSwatch(graphics, 0, 0, -1, zoom);
        }
        verifyNoInteractions(graphics);
    }

    private static Vector3f point(PoseStack pose, int x, int y){
        return pose.last().pose().transformPosition(new Vector3f(x, y, 0));
    }

    private static void assertPoint(Vector3f point, double x, double y){
        assertEquals(x, point.x, 0.001);
        assertEquals(y, point.y, 0.001);
    }
}
