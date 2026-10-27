package com.rieno.gadgetsandgizmos.lib.client.view;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.view.ControlledViewSource;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlAction;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlInput;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;

// Fit the standard camera overlay and its pointer regions to a resizable feed
public final class ViewFeedControls{
    private final ViewControlPanel controls = new ViewControlPanel();
    private Layout layout = layout(640, 360);
    private ViewReference source;

    // Keep the control layout readable in small feeds without changing its aspect ratio
    public static Layout layout(int width, int height){
        float scale = Math.min(Math.max(1, width) / 640F, Math.max(1, height) / 360F);
        return new Layout(scale, Math.max(1, Math.round(width / scale)), Math.max(1, Math.round(height / scale)));
    }

    // Draw over a GUI feed using the host's existing element transform
    public void render(GuiGraphics graphics, Font font, ViewReference ref, int x, int y, int width, int height,
                       BiConsumer<ViewControlAction, ViewControlInput> actions){
        var camera = prepare(ref, width, height, actions);
        if(camera == null) return;
        graphics.flush();
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(x, y, 1);
            graphics.pose().scale(layout.scale(), layout.scale(), 1);
            controls.renderOverlay(graphics, font, 0, 0, layout.width(), layout.height(),
                    camera.viewControlState(), input -> actions.accept(ViewControlAction.CONTROL, input));
            graphics.flush();
        }finally{
            graphics.pose().popPose();
        }
    }

    // Draw over a world feed without allocating another scene or GUI framebuffer
    public void renderSurface(Font font, ViewReference ref, int width, int height, float z,
                              PoseStack stack, MultiBufferSource buffers,
                              BiConsumer<ViewControlAction, ViewControlInput> actions){
        var camera = prepare(ref, width, height, actions);
        if(camera == null) return;
        stack.pushPose();
        try{
            stack.scale(layout.scale(), layout.scale(), 1);
            controls.renderOverlay(new ProjectedViewControlCanvas(stack, buffers,
                            ResourceLocation.withDefaultNamespace("textures/misc/white.png"), z - .2F),
                    font, 0, 0, layout.width(), layout.height(), camera.viewControlState(),
                    input -> actions.accept(ViewControlAction.CONTROL, input));
        }finally{
            stack.popPose();
        }
    }

    // Accept normalized element coordinates after the host has undone rotation and scale
    public boolean mouseClicked(double x, double y, int button){
        return source != null && controls.mouseClicked(x * layout.width(), y * layout.height(), button);
    }
    public boolean mouseDragged(double x, double y){
        return source != null && controls.mouseDragged(x * layout.width(), y * layout.height());
    }
    public boolean isDragging(){ return controls.isDragging(); }
    public void mouseReleased(){ controls.mouseReleased(); }

    // Release stale drags whenever the selected source disappears or changes
    private ControlledViewSource prepare(ViewReference ref, int width, int height,
            BiConsumer<ViewControlAction, ViewControlInput> actions){
        var resolved = ref == null ? null : ref.resolve(Minecraft.getInstance().level);
        if(!(resolved instanceof ControlledViewSource camera) || width <= 0 || height <= 0){
            controls.setSource(null);
            source = null;
            return null;
        }
        source = ref;
        controls.setSource(ref);
        layout = layout(width, height);
        controls.setTakeControlAction(() -> actions.accept(ViewControlAction.TAKE_CONTROL, null));
        controls.setInteractAction(() -> actions.accept(ViewControlAction.INTERACT, null));
        return camera;
    }

    public record Layout(float scale, int width, int height){}
}
