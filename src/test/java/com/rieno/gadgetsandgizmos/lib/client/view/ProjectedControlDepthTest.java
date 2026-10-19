package com.rieno.gadgetsandgizmos.lib.client.view;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.joml.Matrix4f;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectedControlDepthTest{
    @Test void projectedForegroundPrimitivesHaveDistinctDepths(){
        var vertices = mock(VertexConsumer.class, RETURNS_SELF);
        var buffers = mock(MultiBufferSource.class);
        when(buffers.getBuffer(any(RenderType.class))).thenReturn(vertices);
        var stack = new PoseStack();
        stack.translate(0, 0, 3);
        var canvas = new ProjectedViewControlCanvas(stack, buffers,
                ResourceLocation.withDefaultNamespace("textures/misc/white.png"), 2);
        canvas.fill(0, 0, 20, 20, 0xFFFFFFFF);
        canvas.layer(1).fill(2, 2, 18, 18, 0xFFFFFFFF);
        canvas.layer(1).layer(1).fill(4, 4, 16, 16, 0xFFFFFFFF);
        var depths = ArgumentCaptor.forClass(Float.class);
        verify(vertices, times(12)).addVertex(any(Matrix4f.class), anyFloat(), anyFloat(), depths.capture());
        assertTrue(depths.getAllValues().get(0) > depths.getAllValues().get(4));
        assertTrue(depths.getAllValues().get(4) > depths.getAllValues().get(8));
        assertSame(stack, canvas.stack());
        assertEquals(3, stack.last().pose().m32(), 1e-6);
    }
}
