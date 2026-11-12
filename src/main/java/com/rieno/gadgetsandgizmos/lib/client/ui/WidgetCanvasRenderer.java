package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.lib.display.WidgetCanvasProjection;
import com.rieno.gadgetsandgizmos.lib.display.WidgetElementLayout;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.function.UnaryOperator;

// Project resolved widget elements through one shared canvas transform
public final class WidgetCanvasRenderer{
    // Keep canvas rendering independent of a particular display or controller
    private WidgetCanvasRenderer(){}

    // Fit an inspected canvas without stretching its contents or escaping the preview bounds
    public static boolean preview(GuiGraphics graphics, GraphValue val, int x, int y, int width, int height,
                                  ElementPainter painter){
        return preview(graphics, val, x, y, width, height, Frame.NONE, (ctx, w, h) -> {}, painter);
    }

    // Fit the frame and canvas together before clipping elements to the authored content area
    public static boolean preview(GuiGraphics graphics, GraphValue val, int x, int y, int width, int height,
                                  Frame frame, FramePainter background, ElementPainter painter){
        if(val == null || !GraphWidgetValue.TYPE.equals(val.type()) || width <= 0 || height <= 0) return false;
        CompoundTag data = GraphWidgetValue.payload(val);
        int canvasWidth = WidgetCanvasProjection.canvasWidth(data), canvasHeight = WidgetCanvasProjection.canvasHeight(data);
        int frameWidth = canvasWidth + frame.left() + frame.right();
        int frameHeight = canvasHeight + frame.top() + frame.bottom();
        var fit = GuiCanvasTransform.fit(frameWidth, frameHeight, width, height, 0, Float.MAX_VALUE);
        try(var clip = GuiPoseScissor.push(graphics, x, y, x + width, y + height)){
            graphics.pose().pushPose();
            try{
                graphics.pose().translate(x + fit.x(), y + fit.y(), 0);
                graphics.pose().scale(fit.scale(), fit.scale(), 1);
                background.draw(graphics, canvasWidth, canvasHeight);
                try(var canvasClip = GuiPoseScissor.push(graphics, frame.left(), frame.top(),
                        frame.left() + canvasWidth, frame.top() + canvasHeight)){
                    var canvas = new WidgetCanvasProjection.Layout(frame.left(), frame.top(), 0,
                            canvasWidth, canvasHeight, canvasWidth, canvasHeight, 0);
                    render(graphics.pose(), canvas, data.getList("WidgetElements", net.minecraft.nbt.Tag.TAG_COMPOUND),
                            data.getBoolean("Constrain"), UnaryOperator.identity(), painter);
                }
            }finally{ graphics.pose().popPose(); }
        }
        return true;
    }

    // Reserve authored pixels around a canvas without tying its artwork to a particular host
    public record Frame(int left, int top, int right, int bottom){
        public static final Frame NONE = new Frame(0, 0, 0, 0);
        public Frame{
            if(left < 0 || top < 0 || right < 0 || bottom < 0) throw new IllegalArgumentException("Frame insets must be nonnegative");
        }
    }

    // Paint the host's backdrop in authored coordinates before its elements
    @FunctionalInterface public interface FramePainter{
        void draw(GuiGraphics graphics, int canvasWidth, int canvasHeight);
    }

    // Restore both canvas and element poses even if a host painter cannot draw its content
    public static void render(PoseStack poses, WidgetCanvasProjection.Layout canvas, ListTag elements,
                              boolean constrain, UnaryOperator<CompoundTag> resolve, ElementPainter painter){
        poses.pushPose();
        try{
            poses.translate(canvas.x() + canvas.width() / 2, canvas.y() + canvas.height() / 2, canvas.z());
            poses.mulPose(Axis.ZP.rotationDegrees((float)canvas.rotation()));
            poses.translate(-canvas.width() / 2, -canvas.height() / 2, 0);
            poses.scale((float)canvas.scaleX(), (float)canvas.scaleY(), 1);
            for(int idx = 0; idx < elements.size(); idx++){
                CompoundTag elm = resolve.apply(elements.getCompound(idx).copy());
                if(elm == null || elm.contains("Visible") && !elm.getBoolean("Visible")) continue;
                var layout = WidgetElementLayout.resolve(elm, canvas.canvasWidth(), canvas.canvasHeight(), constrain);
                poses.pushPose();
                try{
                    HudWidgetRenderer.transform(poses, layout);
                    painter.draw(elm, layout.bounds().width(), layout.bounds().height());
                }finally{ poses.popPose(); }
            }
        }finally{ poses.popPose(); }
    }

    // Bind host-owned content and media after the reusable geometry has been applied
    @FunctionalInterface
    public interface ElementPainter{
        // Draw one resolved element in its own local pixel coordinates
        void draw(CompoundTag element, int width, int height);
    }
}
