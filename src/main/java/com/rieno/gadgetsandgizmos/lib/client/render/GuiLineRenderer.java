package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/** Draws a line segment as one GPU quad instead of a rectangle per pixel. */
public final class GuiLineRenderer {
    private GuiLineRenderer() {
    }

    // Batch a thick segment with square caps so adjoining wire segments remain closed
    public static void add(VertexConsumer vertices, Matrix4f pose, double x1, double y1, double x2, double y2, double width, int color){
        double dx = x2 - x1, dy = y2 - y1, length = Math.hypot(dx, dy);
        if(length < 1.0E-8D || width <= 0) return;
        double half = width / 2, capX = dx * half / length, capY = dy * half / length;
        double nx = -capY, ny = capX;
        vertices.addVertex(pose, (float)(x1 - capX - nx), (float)(y1 - capY - ny), 0).setColor(color);
        vertices.addVertex(pose, (float)(x1 - capX + nx), (float)(y1 - capY + ny), 0).setColor(color);
        vertices.addVertex(pose, (float)(x2 + capX + nx), (float)(y2 + capY + ny), 0).setColor(color);
        vertices.addVertex(pose, (float)(x2 + capX - nx), (float)(y2 + capY - ny), 0).setColor(color);
    }

    public static void drawClipped(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                   int left, int top, int right, int bottom, int color) {
        ScreenLineClipper.Segment line = ScreenLineClipper.clip(
                x1, y1, x2, y2, left, top, right, bottom);
        if (line == null) return;
        draw(graphics, (int) Math.round(line.x1()), (int) Math.round(line.y1()),
                (int) Math.round(line.x2()), (int) Math.round(line.y2()), color);
    }

    public static void draw(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length == 0.0F) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        float normalX = -dy * 0.5F / length;
        float normalY = dx * 0.5F / length;
        float startX = x1 + 0.5F;
        float startY = y1 + 0.5F;
        float endX = x2 + 0.5F;
        float endY = y2 + 0.5F;
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.gui());
        vertices.addVertex(pose, startX - normalX, startY - normalY, 0.0F).setColor(color);
        vertices.addVertex(pose, startX + normalX, startY + normalY, 0.0F).setColor(color);
        vertices.addVertex(pose, endX + normalX, endY + normalY, 0.0F).setColor(color);
        vertices.addVertex(pose, endX - normalX, endY - normalY, 0.0F).setColor(color);
    }
}
