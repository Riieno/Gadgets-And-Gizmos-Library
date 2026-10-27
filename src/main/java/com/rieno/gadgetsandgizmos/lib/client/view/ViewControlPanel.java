package com.rieno.gadgetsandgizmos.lib.client.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.config.ViewfinderConfig;
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
    private com.rieno.gadgetsandgizmos.lib.view.ViewReference source;
    private Runnable takeControlAction;
    private Runnable interactAction;
    private final long viewStarted = net.minecraft.Util.getMillis();
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

    // Bind viewfinder buttons to the source currently shown by this host
    public void setSource(com.rieno.gadgetsandgizmos.lib.view.ViewReference source){
        if(java.util.Objects.equals(this.source, source)) return;
        mouseReleased();
        this.source = source;
    }

    // Let the host authorize first-person takeover from the floating icon row
    public void setTakeControlAction(Runnable action){ takeControlAction = action; }
    // Let the host route a hit interaction through its own authenticated source selection
    public void setInteractAction(Runnable action){ interactAction = action; }

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
        graphics.fill(flashLeft, bottom - 34, layout.flashlight().x() + layout.flashlight().width(), bottom - 12,
                state.flashlight() ? 0xFF829445 : 0xFF304758);
        graphics.drawString(font, font.plainSubstrByWidth("Flashlight", Math.max(1, layout.flashlight().width() - 8)),
                flashLeft + 4, bottom - 27, 0xFFF0F6FA, false);
        trackingIcon(graphics, layout.tracking(), state.autoTracking());
        targetLockIcon(graphics, layout.targetLock(), state.lockTarget());
        if(takeControlAction != null) takeControlIcon(graphics, layout.takeControl());
        tick();
    }
    // Render floating controls without covering the scene with a panel background
    public void renderOverlay(Canvas graphics, Font font, int x, int y, int width, int height,
                              ViewControlState state, Consumer<ViewControlInput> sender){
        var layout = ViewControlLayout.overlay(x, y, width, height);
        ViewfinderOverlay.render(graphics, font, x, y, width, height, ViewfinderConfig.frame(source), ViewfinderConfig.grid(source),
                state.fov(), net.minecraft.Util.getMillis() - viewStarted, 0,
                layout.flashlight().x() + layout.flashlight().width() - x + 4, layout.radius() * 2 + 12);
        renderControls(graphics, font, width, height, layout, state, sender);
    }
    // Share the floating controls with GUI hosts while preserving their transforms
    public void renderOverlay(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                              ViewControlState state, Consumer<ViewControlInput> sender){
        renderOverlay(new Canvas(){
            @Override public void fill(int left, int top, int right, int bottom, int color){ graphics.fill(left, top, right, bottom, color); }
            @Override public void drawString(Font font, String text, int x, int y, int color, boolean shadow){ graphics.drawString(font, text, x, y, color, shadow); }
        }, font, x, y, width, height, state, sender);
    }
    public void renderChin(Canvas graphics, Font font, int x, int y, int width, int height,
                           ViewControlState state, Consumer<ViewControlInput> sender){
        graphics.fill(x, y, x + width, y + height, 0xF0152433);
        renderControls(graphics, font, width, height, ViewControlLayout.chin(x, y, width, height), state, sender);
    }
    private void renderControls(Canvas graphics, Font font, int width, int height, ViewControlLayout layout,
                                ViewControlState state, Consumer<ViewControlInput> sender){
        this.state = state;
        this.sender = sender;
        this.layout = layout;
        center = layout.stickX();
        bottom = layout.stickY() + 25;
        radius = layout.radius();
        circle(graphics, center, layout.stickY(), radius, 0xFF747877);
        Canvas foreground = graphics.layer(1);
        circle(foreground, center + (int) (stickX * radius), layout.stickY() + (int) (stickY * radius),
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
        if(zoom.width() > zoom.height()){
            int knob = zoom.x() + (int) ((120 - state.fov()) / 115 * zoom.width());
            foreground.fill(knob - 2, zoom.y() - 3, knob + 3, zoom.y() + zoom.height() + 3, 0xFFB9DFD9);
        }else{
            int knob = zoom.y() + (int) ((state.fov() - 5) / 115 * zoom.height());
            int halfWidth = Math.max(zoom.width(), width / 50);
            int halfHeight = Math.max(2, Math.min(width, height) / 160);
            foreground.fill(zoom.centerX() - halfWidth, knob - halfHeight,
                    zoom.centerX() + halfWidth, knob + halfHeight, 0xFFB9DFD9);
        }
        var flash = layout.flashlight();
        rounded(graphics, flash, flash.width() / 4, state.flashlight() ? 0xFF77B5AC : 0xFF747877);
        int iconX = flash.x() + flash.width() / 5;
        int iconY = flash.centerY();
        int length = Math.max(5, flash.width() * 3 / 5);
        int half = Math.max(2, flash.height() / 10);
        foreground.fill(iconX, iconY - half, iconX + length * 2 / 3, iconY + half, 0xFFF0F6FA);
        foreground.fill(iconX - 1, iconY - half - 1, iconX + 2, iconY + half + 1, 0xFFF0F6FA);
        for(int row = -half - 1; row <= half + 1; row++){
            foreground.fill(iconX + length * 2 / 3, iconY + row, iconX + length - Math.abs(row) / 2, iconY + row + 1, 0xFFF0F6FA);
        }
        trackingIcon(graphics, layout.tracking(), state.autoTracking());
        targetLockIcon(graphics, layout.targetLock(), state.lockTarget());
        if(takeControlAction != null) takeControlIcon(graphics, layout.takeControl());
        viewfinderIcon(graphics, layout.frame(), false, ViewfinderConfig.frame(source));
        viewfinderIcon(graphics, layout.grid(), true, ViewfinderConfig.grid(source));
        if(interactAction != null) interactIcon(graphics, layout.interact());
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
        if(takeControlAction != null && layout.takeControl().contains(x, y)){
            mouseReleased();
            takeControlAction.run();
            return true;
        }
        if(layout.frame().contains(x, y)){ ViewfinderConfig.toggleFrame(source); return true; }
        if(layout.grid().contains(x, y)){ ViewfinderConfig.toggleGrid(source); return true; }
        if(interactAction != null && layout.interact().contains(x, y)){
            mouseReleased();
            interactAction.run();
            return true;
        }
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
        if(x >= zoom.x() - 4 && x <= zoom.x() + zoom.width() + 4
                && y >= zoom.y() - 5 && y <= zoom.y() + zoom.height() + 5){
            dragging = "zoom";
            drag(x, y);
            return true;
        }
        if(layout.flashlight().contains(x, y)){
            sender.accept(new ViewControlInput(0, 0, -1, "", state.flashlight() ? 0 : 1));
            return true;
        }
        if(layout.tracking().contains(x, y)){
            sender.accept(new ViewControlInput(0, 0, -1, "", -1, state.autoTracking() ? 0 : 1));
            return true;
        }
        if(layout.targetLock().contains(x, y)){
            sender.accept(new ViewControlInput(0, 0, -1, "", -1, -1, state.lockTarget() ? 0 : 1));
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
    // Let hosts release pointer capture when the stick or zoom control is no longer held
    public boolean isDragging(){ return !dragging.isEmpty(); }
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
            double fraction = zoom.height() > zoom.width() ? 1 - Math.clamp((y - zoom.y()) / Math.max(1, zoom.height()), 0, 1)
                    : Math.clamp((x - zoom.x()) / Math.max(1, zoom.width()), 0, 1);
            sender.accept(new ViewControlInput(0, 0, 120 - fraction * 115, "", -1));
        }
    }
    private static void circle(Canvas graphics, int x, int y, int radius, int color){
        com.rieno.gadgetsandgizmos.lib.client.ui.RasterRectangles.circle(x, y, radius,
                (left, top, right, bottom) -> graphics.fill(left, top, right, bottom, color));
    }
    // Draw a targeting reticle whose background shows whether acquisition is enabled
    private static void trackingIcon(Canvas graphics, ViewControlLayout.Bounds bounds, boolean enabled){
        if(bounds.width() <= 0 || bounds.height() <= 0) return;
        rounded(graphics, bounds, bounds.width() / 4, enabled ? 0xFF77B5AC : 0xFF747877);
        graphics = graphics.layer(1);
        int inset = Math.max(3, bounds.width() / 5);
        int left = bounds.x() + inset;
        int right = bounds.x() + bounds.width() - inset;
        int top = bounds.y() + inset;
        int bottom = bounds.y() + bounds.height() - inset;
        int arm = Math.max(2, (right - left) / 3);
        int line = Math.max(1, bounds.width() / 16);
        int color = 0xFFF0F6FA;
        for(int x : new int[]{left, right - line}){
            graphics.fill(x, top, x + line, top + arm, color);
            graphics.fill(x, bottom - arm, x + line, bottom, color);
        }
        for(int y : new int[]{top, bottom - line}){
            graphics.fill(left, y, left + arm, y + line, color);
            graphics.fill(right - arm, y, right, y + line, color);
        }
        circle(graphics, bounds.centerX(), bounds.centerY(), Math.max(1, bounds.width() / 12), color);
    }
    // Show a closed padlock while dynamic target acquisition is disabled
    private static void targetLockIcon(Canvas graphics, ViewControlLayout.Bounds bounds, boolean locked){
        if(bounds.width() <= 0 || bounds.height() <= 0) return;
        rounded(graphics, bounds, bounds.width() / 4, locked ? 0xFF77B5AC : 0xFF747877);
        graphics = graphics.layer(1);
        int line = Math.max(1, bounds.width() / 12);
        int half = Math.max(3, bounds.width() / 4);
        int center = bounds.centerX();
        int middle = bounds.centerY();
        int top = middle - half;
        int color = 0xFFF0F6FA;
        graphics.fill(center - half, middle, center + half + 1, middle + half, color);
        int shift = locked ? 0 : half;
        graphics.fill(center - half + line + shift, top, center + half - line + shift, top + line, color);
        graphics.fill(center - half + line + shift, top, center - half + line * 2 + shift, middle, color);
        graphics.fill(center + half - line * 2 + shift, top, center + half - line + shift, middle, color);
        graphics.layer(1).fill(center, middle + line, center + line, middle + half - line,
                locked ? 0xFF77B5AC : 0xFF747877);
    }
    // Draw a lens inside viewfinder brackets to distinguish takeover from target tracking
    private static void takeControlIcon(Canvas graphics, ViewControlLayout.Bounds bounds){
        if(bounds.width() <= 0 || bounds.height() <= 0) return;
        rounded(graphics, bounds, bounds.width() / 4, 0xFF747877);
        graphics = graphics.layer(1);
        int inset = Math.max(3, bounds.width() / 5);
        int left = bounds.x() + inset, right = bounds.x() + bounds.width() - inset;
        int top = bounds.y() + inset, bottom = bounds.y() + bounds.height() - inset;
        int line = Math.max(1, bounds.width() / 12), length = Math.max(3, (right - left) / 3);
        for(int x : new int[]{left, right - line}) for(int y : new int[]{top, bottom - line}){
            graphics.fill(x == left ? x : right - length, y, x == left ? left + length : right, y + line, 0xFFF0F6FA);
            graphics.fill(x, y == top ? y : bottom - length, x + line, y == top ? top + length : bottom, 0xFFF0F6FA);
        }
        circle(graphics, bounds.centerX(), bounds.centerY(), Math.max(2, bounds.width() / 8), 0xFFF0F6FA);
    }
    private static void viewfinderIcon(Canvas graphics, ViewControlLayout.Bounds bounds, boolean grid, boolean active){
        if(bounds.width() <= 0 || bounds.height() <= 0) return;
        rounded(graphics, bounds, bounds.width() / 4, active ? 0xFF77B5AC : 0xFF747877);
        graphics = graphics.layer(1);
        int pad = Math.max(3, bounds.width() / 5), line = Math.max(1, bounds.width() / 12);
        int left = bounds.x() + pad, right = bounds.x() + bounds.width() - pad;
        int top = bounds.y() + pad, bottom = bounds.y() + bounds.height() - pad;
        if(grid){
            for(int idx = 1; idx < 3; idx++){
                int x = left + (right - left) * idx / 3, y = top + (bottom - top) * idx / 3;
                graphics.fill(x, top, x + line, bottom, 0xFFF0F6FA);
                graphics.fill(left, y, right, y + line, 0xFFF0F6FA);
            }
        }else{
            int length = Math.max(3, (right - left) / 3);
            for(int x : new int[]{left, right - line}) for(int y : new int[]{top, bottom - line}){
                graphics.fill(x == left ? x : right - length, y, x == left ? left + length : right, y + line, 0xFFF0F6FA);
                graphics.fill(x, y == top ? y : bottom - length, x + line, y == top ? top + length : bottom, 0xFFF0F6FA);
            }
        }
    }
    private static void interactIcon(Canvas graphics, ViewControlLayout.Bounds bounds){
        rounded(graphics, bounds, bounds.width() / 4, 0xFF747877);
        graphics = graphics.layer(1);
        int x = bounds.x() + bounds.width() / 4, y = bounds.y() + bounds.height() / 4;
        int size = Math.max(6, bounds.width() / 2), line = Math.max(1, bounds.width() / 12);
        for(int row = 0; row < size; row++) graphics.fill(x, y + row, x + Math.max(1, size - row), y + row + 1, 0xFFF0F6FA);
        for(int idx = size / 2; idx < size; idx++) graphics.fill(x + idx, y + idx, x + idx + line, y + idx + line, 0xFFF0F6FA);
    }
    private static void rounded(Canvas graphics, ViewControlLayout.Bounds bounds, int radius, int color){
        com.rieno.gadgetsandgizmos.lib.client.ui.RasterRectangles.rounded(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius,
                (left, top, right, bottom) -> graphics.fill(left, top, right, bottom, color));
    }
    // Let a host retain its own depth, projection and buffer state
    public interface Canvas{
        // Select an overlay layer while flat GUI canvases retain ordinary draw order
        default Canvas layer(int index){ return this; }
        void fill(int left, int top, int right, int bottom, int color);
        void drawString(Font font, String text, int x, int y, int color, boolean shadow);
        default void drawCenteredString(Font font, String text, int x, int y, int color){
            drawString(font, text, x - font.width(text) / 2, y, color, true);
        }
    }
}
