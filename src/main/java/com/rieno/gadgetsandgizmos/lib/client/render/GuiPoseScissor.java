package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Clip local canvas geometry in screen coordinates while preserving the parent's clip.
public final class GuiPoseScissor implements AutoCloseable{
    private final GuiGraphics graphics;

    private GuiPoseScissor(GuiGraphics graphics, int left, int top, int right, int bottom){
        this.graphics = graphics;
        ScreenRectangle clip = bounds(graphics.pose().last().pose(), left, top, right, bottom);
        graphics.flush();
        graphics.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
    }

    public static GuiPoseScissor push(GuiGraphics graphics, int left, int top, int right, int bottom){
        return new GuiPoseScissor(graphics, left, top, right, bottom);
    }

    // Include every transformed corner so mirrored and rotated canvases keep usable bounds.
    public static ScreenRectangle bounds(Matrix4f pose, int left, int top, int right, int bottom){
        Vector3f a = pose.transformPosition(new Vector3f(left, top, 0));
        Vector3f b = pose.transformPosition(new Vector3f(right, top, 0));
        Vector3f c = pose.transformPosition(new Vector3f(left, bottom, 0));
        Vector3f d = pose.transformPosition(new Vector3f(right, bottom, 0));
        int x1 = (int)Math.floor(Math.min(Math.min(a.x(), b.x()), Math.min(c.x(), d.x())));
        int y1 = (int)Math.floor(Math.min(Math.min(a.y(), b.y()), Math.min(c.y(), d.y())));
        int x2 = (int)Math.ceil(Math.max(Math.max(a.x(), b.x()), Math.max(c.x(), d.x())));
        int y2 = (int)Math.ceil(Math.max(Math.max(a.y(), b.y()), Math.max(c.y(), d.y())));
        return new ScreenRectangle(x1, y1, x2 - x1, y2 - y1);
    }

    @Override
    public void close(){
        try{ graphics.flush(); }
        finally{ graphics.disableScissor(); }
    }
}
