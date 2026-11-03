package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

// Scale a native menu canvas and all pointer input together, retaining vanilla slot behavior
public abstract class ScaledMenuScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M>{
    private boolean canvasInput;
    protected ScaledMenuScreen(M menu, Inventory inventory, Component title, int canvasWidth, int canvasHeight){
        super(menu, inventory, title);
        imageWidth = canvasWidth;
        imageHeight = canvasHeight;
    }

    @Override protected void init(){ super.init(); leftPos = 0; topPos = 0; }
    protected int canvasHeight(){ return imageHeight; }

    // Supply the host's normal GUI size to prevent the viewport from growing to fill a large window
    protected TabletLayout preferredCanvasSize(){ return null; }

    public final TabletLayout.Bounds viewport(){
        var preferred = preferredCanvasSize();
        if(preferred != null){
            double scale = Math.min(1, Math.min(Math.max(1, width - 16) / (double)preferred.logicalWidth(),
                    Math.max(1, height - 16) / (double)preferred.logicalHeight()));
            int drawWidth = Math.max(1, (int)Math.round(preferred.logicalWidth() * scale));
            int drawHeight = Math.max(1, (int)Math.round(preferred.logicalHeight() * scale));
            return new TabletLayout.Bounds((width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight,
                    drawWidth / (double)imageWidth, drawHeight / (double)canvasHeight());
        }
        return new TabletLayout(imageWidth, canvasHeight()).fit(8, 8, Math.max(1, width - 16), Math.max(1, height - 16));
    }

    // Report screen-space bounds to inventory overlays while vanilla slots keep canvas coordinates
    @Override public int getGuiLeft(){ return viewport().left(); }
    @Override public int getGuiTop(){ return viewport().top(); }
    @Override public int getXSize(){ return viewport().width(); }
    @Override public int getYSize(){ return viewport().height(); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick){
        imageHeight = canvasHeight();
        graphics.fillGradient(0, 0, width, height, 0xE0050910, 0xE00D1420);
        // Screen events and inventory overlays must receive the real window coordinates
        super.render(graphics, mouseX, mouseY, tick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void canvasPose(GuiGraphics graphics, Runnable draw){
        var bounds = viewport();
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(bounds.left(), bounds.top(), 0);
            graphics.pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            draw.run();
        }finally{ graphics.pose().popPose(); }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float tick){
        canvasPose(graphics, () -> renderBg(graphics, tick, (int)localX(mouseX), (int)localY(mouseY)));
    }
    @Override protected void renderSlot(GuiGraphics graphics, Slot slot){
        canvasPose(graphics, () -> super.renderSlot(graphics, slot));
    }
    @Override protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float tick){
        canvasPose(graphics, () -> super.renderSlotHighlight(graphics, slot, (int)localX(mouseX), (int)localY(mouseY), tick));
    }
    @Override protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY){
        return super.isHovering(x, y, width, height, canvasInput ? mouseX : localX(mouseX), canvasInput ? mouseY : localY(mouseY));
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY){}
    @Override public final boolean mouseClicked(double x, double y, int btn){
        canvasInput = true;
        try{ return canvasClicked(localX(x), localY(y), btn); }finally{ canvasInput = false; }
    }
    protected boolean canvasClicked(double x, double y, int btn){ return super.mouseClicked(x, y, btn); }
    @Override public final boolean mouseReleased(double x, double y, int btn){
        canvasInput = true;
        try{ return canvasReleased(localX(x), localY(y), btn); }finally{ canvasInput = false; }
    }
    protected boolean canvasReleased(double x, double y, int btn){ return super.mouseReleased(x, y, btn); }
    @Override public final boolean mouseDragged(double x, double y, int btn, double dx, double dy){
        canvasInput = true;
        try{ return canvasDragged(localX(x), localY(y), btn, dx / viewport().scaleX(), dy / viewport().scaleY()); }
        finally{ canvasInput = false; }
    }
    protected boolean canvasDragged(double x, double y, int btn, double dx, double dy){ return super.mouseDragged(x, y, btn, dx, dy); }
    @Override public final boolean mouseScrolled(double x, double y, double sx, double sy){
        canvasInput = true;
        try{ return canvasScrolled(localX(x), localY(y), sx, sy); }finally{ canvasInput = false; }
    }
    protected boolean canvasScrolled(double x, double y, double sx, double sy){ return super.mouseScrolled(x, y, sx, sy); }
    private double localX(double x){ var bounds = viewport(); return (x - bounds.left()) / bounds.scaleX(); }
    private double localY(double y){ var bounds = viewport(); return (y - bounds.top()) / bounds.scaleY(); }
}
