package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3f;

// Keep buffered panel text and geometry inside the same raised GUI layer
public final class GuiRenderLayer implements AutoCloseable{
    private static final float ITEM_DEPTH_SCALE = 0.001F;
    private final GuiGraphics graphics;

    private GuiRenderLayer(GuiGraphics graphics, float depth){
        this.graphics = graphics;
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, depth);
    }

    // Flush the preceding panel before drawing an overlay at the requested relative depth
    public static GuiRenderLayer push(GuiGraphics graphics, float depth){
        return new GuiRenderLayer(graphics, depth);
    }

    // Keep vanilla slots, carried items, text and widgets just above their modal background
    public static GuiRenderLayer pushFlattened(GuiGraphics graphics, float depth){
        GuiRenderLayer layer = new GuiRenderLayer(graphics, depth);
        scaleDepth(graphics, ITEM_DEPTH_SCALE);
        return layer;
    }

    // Render the background normally inside a matching flattened foreground pass
    public static GuiRenderLayer restoreBackground(GuiGraphics graphics, float depth){
        GuiRenderLayer layer = new GuiRenderLayer(graphics, 0);
        scaleDepth(graphics, 1 / ITEM_DEPTH_SCALE);
        graphics.pose().translate(0, 0, -depth);
        return layer;
    }

    // Depth compression must keep ordinary GUI item lighting
    private static void scaleDepth(GuiGraphics graphics, float scale){
        Matrix3f normal = new Matrix3f(graphics.pose().last().normal());
        graphics.pose().scale(1, 1, scale);
        graphics.pose().last().normal().set(normal);
    }

    // Finish this layer before restoring its caller's pose
    @Override
    public void close(){
        try{ graphics.flush(); }
        finally{ graphics.pose().popPose(); }
    }
}
