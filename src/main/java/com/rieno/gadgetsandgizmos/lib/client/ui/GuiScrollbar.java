package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

// Draw and drag a scrollbar for an integer range
public final class GuiScrollbar{
    private final GuiTextureRegion track;
    private final GuiTextureRegion thumb;
    private final int trackColor;
    private final int thumbColor;
    private int x, y, width, height, thumbHeight, maximum, value;
    private boolean dragging;
    private double dragOffset;

    public GuiScrollbar(GuiTextureRegion track, GuiTextureRegion thumb){
        this.track = Objects.requireNonNull(track);
        this.thumb = Objects.requireNonNull(thumb);
        trackColor = 0;
        thumbColor = 0;
    }

    // Use solid colors when the surrounding panel has no painted scrollbar partials
    public GuiScrollbar(int trackColor, int thumbColor){
        track = null;
        thumb = null;
        this.trackColor = trackColor;
        this.thumbColor = thumbColor;
    }

    // Synchronize the screen bounds and range without interrupting an active drag
    public void configure(int x, int y, int width, int height, int maximum, int value){
        int thumbHeight = thumb == null ? 12 : Math.round(thumb.height() * width / (float)thumb.width());
        configure(x, y, width, height, maximum, value, thumbHeight);
    }

    // Size the thumb to the visible fraction of a scrollable list or document
    public void configure(int x, int y, int width, int height, int maximum, int value, int thumbHeight){
        if(width <= 0 || height <= 0) throw new IllegalArgumentException("Scrollbar dimensions must be positive");
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.thumbHeight = Math.clamp(thumbHeight, 1, height);
        this.maximum = Math.max(0, maximum);
        this.value = Math.clamp(value, 0, this.maximum);
        if(this.maximum == 0 || height == this.thumbHeight) dragging = false;
    }

    public int value(){ return value; }
    public double fraction(){ return maximum == 0 ? 0 : value / (double)maximum; }

    public boolean contains(double mouseX, double mouseY){
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void render(GuiGraphics graphics){
        if(track == null){
            render(graphics, trackColor, thumbColor);
            return;
        }
        int trackWidth = Math.max(1, Math.round(track.width() * width / (float)thumb.width()));
        track.draw(graphics, x + (width - trackWidth) / 2, y, trackWidth, height);
        if(maximum > 0) thumb.draw(graphics, x, thumbTop(), width, thumbHeight);
    }

    // Keep solid scrollbars in sync with the active panel theme
    public void render(GuiGraphics graphics, int trackColor, int thumbColor){
        if(maximum == 0) return;
        graphics.fill(x, y, x + width, y + height, trackColor);
        graphics.fill(x, thumbTop(), x + width, thumbTop() + thumbHeight, thumbColor);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button){
        if(button != 0 || maximum == 0 || height <= thumbHeight || !contains(mouseX, mouseY)) return false;
        int top = thumbTop();
        dragOffset = mouseY >= top && mouseY < top + thumbHeight ? mouseY - top : thumbHeight / 2D;
        dragging = true;
        dragTo(mouseY);
        return true;
    }

    public boolean mouseDragged(double mouseY, int button){
        if(!dragging || button != 0) return false;
        dragTo(mouseY);
        return true;
    }

    public boolean mouseReleased(){
        boolean wasDragging = dragging;
        dragging = false;
        return wasDragging;
    }

    public void reset(){
        dragging = false;
        value = 0;
    }

    private int thumbTop(){ return y + (int)Math.round(fraction() * (height - thumbHeight)); }

    private void dragTo(double mouseY){
        double fraction = Math.clamp((mouseY - y - dragOffset) / (height - thumbHeight), 0, 1);
        value = (int)Math.round(fraction * maximum);
    }
}
