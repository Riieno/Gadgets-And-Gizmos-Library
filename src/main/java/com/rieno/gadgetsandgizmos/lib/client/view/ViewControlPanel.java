package com.rieno.gadgetsandgizmos.lib.client.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.view.ViewControlInput;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlState;
import com.rieno.gadgetsandgizmos.lib.view.ViewRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

// Share a draggable analogue stick, zoom slider and source settings across hosts
public final class ViewControlPanel{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private Consumer<ViewControlInput> sender = input -> {};
    private ViewControlState state = new ViewControlState(ViewRig.Mode.LOCKED, 70, false);
    private int left;
    private int right;
    private int center;
    private int bottom;
    private int radius = 18;
    private ViewControlLayout layout;
    private String dragging = "";
    private double stickX;
    private double stickY;
    private long sentTick = Long.MIN_VALUE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw controls at the bottom middle of the host's live view
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                       ViewControlState state, Consumer<ViewControlInput> sender){
        render(new Canvas(){
            @Override public void fill(int left, int top, int right, int bottom, int color){ graphics.fill(left, top, right, bottom, color); }
            @Override public void drawString(Font font, String text, int x, int y, int color, boolean shadow){ graphics.drawString(font, text, x, y, color, shadow); }
        }, font, x, y, width, height, state, sender);
    }
    // Draw the same controls on a projected surface without allocating a GUI framebuffer
    public void render(Canvas graphics, Font font, int x, int y, int width, int height,
                       ViewControlState state, Consumer<ViewControlInput> sender){
        this.state = state;
        this.sender = sender;
        layout = ViewControlLayout.compact(x, y, width, height);
        radius = layout.radius();
        center = x + width / 2;
        left = Math.max(x + 4, center - 142);
        right = Math.min(x + width - 4, center + 142);
        bottom = y + height - 4;
        graphics.fill(left, bottom - 72, right, bottom, 0xC9152433);
        int modeWidth = Math.max(1, (right - left - 8) / 3);
        for(ViewRig.Mode mode : ViewRig.Mode.values()){
            int modeLeft = left + 4 + mode.ordinal() * modeWidth;
            graphics.fill(modeLeft, bottom - 68, modeLeft + modeWidth - 2, bottom - 51,
                    mode == state.mode() ? 0xFF44796F : 0xFF304758);
            String label = mode.name().substring(0, 1) + mode.name().substring(1).toLowerCase(java.util.Locale.ROOT);
            graphics.drawCenteredString(font, label, modeLeft + modeWidth / 2, bottom - 64, 0xFFF0F6FA);
        }
        circle(graphics, center, bottom - 25, radius + 2, 0xFF16232C);
        circle(graphics, center, bottom - 25, radius, 0xFF405968);
        circle(graphics, center + (int) (stickX * radius), bottom - 25 + (int) (stickY * radius), 6, 0xFFAFD8D0);
        int zoomLeft = left + 7;
        int zoomRight = center - radius - 10;
        graphics.drawString(font, "Zoom", zoomLeft, bottom - 39, 0xFFE1EDF5, false);
        graphics.fill(zoomLeft, bottom - 21, zoomRight, bottom - 16, 0xFF607482);
        int knob = zoomLeft + (int) ((120 - state.fov()) / 115 * Math.max(1, zoomRight - zoomLeft));
        graphics.fill(knob - 2, bottom - 25, knob + 3, bottom - 12, 0xFFB9DFD9);
        int flashLeft = center + radius + 10;
        graphics.fill(flashLeft, bottom - 34, right - 7, bottom - 12,
                state.flashlight() ? 0xFF829445 : 0xFF304758);
        graphics.drawString(font, font.plainSubstrByWidth("Flashlight", Math.max(1, right - flashLeft - 11)),
                flashLeft + 4, bottom - 27, 0xFFF0F6FA, false);
        tick();
    }
    // Render floating controls without covering the scene with a panel background
    public void renderOverlay(Canvas graphics, Font font, int x, int y, int width, int height,
                              ViewControlState state, Consumer<ViewControlInput> sender){
        this.state = state;
        this.sender = sender;
        layout = ViewControlLayout.overlay(x, y, width, height);
        center = layout.stickX();
        bottom = layout.stickY() + 25;
        radius = layout.radius();
        circle(graphics, center, layout.stickY(), radius, 0xFF747877);
        circle(graphics, center + (int) (stickX * radius), layout.stickY() + (int) (stickY * radius),
                Math.max(4, radius * 3 / 10), 0xFFCED0CE);
        for(ViewRig.Mode mode : ViewRig.Mode.values()){
            var bounds = layout.modes().get(mode.ordinal());
            rounded(graphics, bounds, bounds.height() / 2,
                    mode == state.mode() ? 0xFF77B5AC : 0xFF304758);
            graphics.drawCenteredString(font, mode.name(), bounds.centerX(),
                    bounds.centerY() - font.lineHeight / 2, 0xFFF0F6FA);
        }
        var zoom = layout.zoom();
        graphics.fill(zoom.x(), zoom.y(), zoom.x() + zoom.width(), zoom.y() + zoom.height(), 0xFF607482);
        int knob = zoom.y() + (int) ((state.fov() - 5) / 115 * zoom.height());
        int halfWidth = Math.max(zoom.width(), width / 50);
        int halfHeight = Math.max(2, Math.min(width, height) / 160);
        graphics.fill(zoom.centerX() - halfWidth, knob - halfHeight,
                zoom.centerX() + halfWidth, knob + halfHeight, 0xFFB9DFD9);
        var flash = layout.flashlight();
        rounded(graphics, flash, flash.width() / 4, state.flashlight() ? 0xFF77B5AC : 0xFF747877);
        int iconX = flash.x() + flash.width() / 5;
        int iconY = flash.centerY();
        int length = Math.max(5, flash.width() * 3 / 5);
        int half = Math.max(2, flash.height() / 10);
        graphics.fill(iconX, iconY - half, iconX + length * 2 / 3, iconY + half, 0xFFF0F6FA);
        graphics.fill(iconX - 1, iconY - half - 1, iconX + 2, iconY + half + 1, 0xFFF0F6FA);
        for(int row = -half - 1; row <= half + 1; row++){
            graphics.fill(iconX + length * 2 / 3, iconY + row, iconX + length - Math.abs(row) / 2, iconY + row + 1, 0xFFF0F6FA);
        }
        tick();
    }
    // Apply a held stick at the game tick rate rather than the frame rate
    public void tick(){
        var level = Minecraft.getInstance().level;
        if(level == null || !"stick".equals(dragging) || sentTick == level.getGameTime()) return;
        sentTick = level.getGameTime();
        sender.accept(new ViewControlInput(-stickX * 3, -stickY * 3, -1, "MANUAL", -1));
    }
    // Accept clicks from either a GUI or a projected world surface
    public boolean mouseClicked(double x, double y, int button){
        if(button != 0 && button != 1 || layout == null) return false;
        for(int idx = 0; idx < layout.modes().size(); idx++){
            if(layout.modes().get(idx).contains(x, y)){
                sender.accept(new ViewControlInput(0, 0, -1, ViewRig.Mode.values()[idx].name(), -1));
                return true;
            }
        }
        if(Math.hypot(x - center, y - (bottom - 25)) <= radius + 5){
            dragging = "stick";
            drag(x, y);
            return true;
        }
        var zoom = layout.zoom();
        if(layout.overlay() ? x >= zoom.x() - zoom.width() && x <= zoom.x() + zoom.width() * 2
                && y >= zoom.y() - 5 && y <= zoom.y() + zoom.height() + 5
                : x >= left && x < center - radius - 7 && y >= bottom - 49 && y <= bottom){
            dragging = "zoom";
            drag(x, y);
            return true;
        }
        if(layout.flashlight().contains(x, y)){
            sender.accept(new ViewControlInput(0, 0, -1, "", state.flashlight() ? 0 : 1));
            return true;
        }
        return false;
    }
    // Retain normalized stick deflection until the pointer is released
    public boolean mouseDragged(double x, double y){
        if(dragging.isEmpty()) return false;
        drag(x, y);
        return true;
    }
    // Return the analogue stick to its centre when the host loses pointer capture
    public boolean mouseReleased(){
        boolean active = !dragging.isEmpty();
        dragging = "";
        stickX = 0;
        stickY = 0;
        sentTick = Long.MIN_VALUE;
        return active;
    }
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private void drag(double x, double y){
        if("stick".equals(dragging)){
            double dx = (x - center) / radius;
            double dy = (y - (bottom - 25)) / radius;
            double length = Math.max(1, Math.hypot(dx, dy));
            stickX = dx / length;
            stickY = dy / length;
        }else{
            var zoom = layout.zoom();
            double fraction = layout.overlay() ? 1 - Math.clamp((y - zoom.y()) / Math.max(1, zoom.height()), 0, 1)
                    : Math.clamp((x - zoom.x()) / Math.max(1, zoom.width()), 0, 1);
            sender.accept(new ViewControlInput(0, 0, 120 - fraction * 115, "", -1));
        }
    }
    private static void circle(Canvas graphics, int x, int y, int radius, int color){
        for(int row = -radius; row <= radius; row++){
            int half = (int) Math.sqrt(radius * radius - row * row);
            graphics.fill(x - half, y + row, x + half + 1, y + row + 1, color);
        }
    }
    private static void rounded(Canvas graphics, ViewControlLayout.Bounds bounds, int radius, int color){
        for(int row = 0; row < bounds.height(); row++){
            int dy = row < radius ? radius - row - 1 : row >= bounds.height() - radius ? row - bounds.height() + radius : 0;
            int inset = radius - (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            graphics.fill(bounds.x() + inset, bounds.y() + row, bounds.x() + bounds.width() - inset, bounds.y() + row + 1, color);
        }
    }
    // Let a host retain its own depth, projection and buffer state
    public interface Canvas{
        void fill(int left, int top, int right, int bottom, int color);
        void drawString(Font font, String text, int x, int y, int color, boolean shadow);
        default void drawCenteredString(Font font, String text, int x, int y, int color){
            drawString(font, text, x - font.width(text) / 2, y, color, true);
        }
    }
}
