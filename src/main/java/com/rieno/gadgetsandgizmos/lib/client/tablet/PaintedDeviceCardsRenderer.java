package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal;
import com.rieno.gadgetsandgizmos.lib.tablet.ControlCardGrid;
import com.rieno.gadgetsandgizmos.lib.tablet.ControlCardStyle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

// Browse bound devices using the same painted cards and grid as wireless controls
public class PaintedDeviceCardsRenderer implements TabletAppClientRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final TabletLayout CANVAS = new TabletLayout(320, 180);
    private static final TabletLayout.Rect CONTENT = new TabletLayout.Rect(6, 24, 308, 141);
    private final PaintedCardArtwork artwork;
    private final ColorPickerModal.TextureAppearance colorAppearance;

    public enum View{
        CONTROL("Control", "ControlAvailable", 0x40BAD2), GRAPH("Graph", "GraphAvailable", 0xB58AE3), PLOT("Plot", "PlotAvailable", 0x6BCB89);
        private final String label, availability;
        private final int color;
        View(String label, String availability, int color){ this.label = label; this.availability = availability; this.color = color; }
    }

    private record Hit(TabletLayout.Rect area, Runnable action, String tooltip){}
    private static final class State implements TabletAppClientState{
        private final List<Hit> hits = new ArrayList<>();
        private int page;
        private final Map<String, Integer> colors = new HashMap<>();
        private ColorPickerModal picker;
        private String colorKey = "";

        @Override public CompoundTag saveDraft(){ var tag = new CompoundTag(); tag.putInt("Page", page); return tag; }
        @Override public void loadDraft(CompoundTag tag){ page = Math.max(0, tag.getInt("Page")); }
    }

    public PaintedDeviceCardsRenderer(ResourceLocation atlas){ this(atlas, null); }
    public PaintedDeviceCardsRenderer(ResourceLocation atlas, ColorPickerModal.TextureAppearance colors){
        artwork = new PaintedCardArtwork(atlas); colorAppearance = colors;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public boolean ownsTabletSurface(){ return true; }
    @Override public TabletAppClientState createScreenState(){ return new State(); }

    // Consumers map these callbacks to their own binding and remote-session actions
    protected void addDevice(TabletAppClientContext ctx){ ctx.actions().send("add_device", ""); }
    protected void renameDevice(TabletAppClientContext ctx, String key, String name){ ctx.actions().send("rename_device", key + "|" + name); }
    protected void removeDevice(TabletAppClientContext ctx, String key){ ctx.actions().send("remove_device", key); }
    protected void colorDevice(TabletAppClientContext ctx, String key, int color){ ctx.actions().send("color_device", key + "|" + String.format(java.util.Locale.ROOT, "%06X", color & 0xFFFFFF)); }
    protected void openDevice(TabletAppClientContext ctx, String key, View view){ ctx.actions().send(view.name().toLowerCase(java.util.Locale.ROOT), key); }

    @Override public void render(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = state(raw);
        var devices = ctx.data().getList("Devices", Tag.TAG_COMPOUND);
        if(state.picker != null && !state.picker.isOpen()){ state.picker = null; state.colorKey = ""; }
        if(ctx.data().contains("Error") && state.picker == null) state.colors.clear();
        var keys = new java.util.HashSet<String>();
        for(int idx = 0; idx < devices.size(); idx++) keys.add(devices.getCompound(idx).getString("Key"));
        state.colors.keySet().retainAll(keys);
        int maximum = devices.size() / 4;
        state.page = Math.clamp(state.page, 0, maximum);
        int first = state.page * 4, count = Math.min(4, devices.size() + 1 - first);
        var cells = ControlCardGrid.arrange(Collections.nCopies(count, ControlCardStyle.Size.WIDE), 4);
        state.hits.clear();
        var bounds = bounds(ctx);
        double mouseX = (ctx.mouseX() - bounds.left()) / bounds.scaleX(), mouseY = (ctx.mouseY() - bounds.top()) / bounds.scaleY();
        var graphics = ctx.graphics();
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(bounds.left(), bounds.top(), 0);
            graphics.pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            artwork.background(graphics);
            try(var clip = GuiPoseScissor.push(graphics, CONTENT.left(), CONTENT.top(), CONTENT.left() + CONTENT.width(), CONTENT.top() + CONTENT.height())){
                for(int idx = 0; idx < cells.size(); idx++){
                    var cell = cells.get(idx);
                    int x = 7 + cell.column() * 78, y = 26 + cell.row() * 72;
                    int width = cell.columns() * 78 - 6, height = cell.rows() * 72 - 6;
                    if(y + height <= CONTENT.top() || y >= CONTENT.top() + CONTENT.height()) continue;
                    if(first + idx == devices.size()){
                        artwork.frame(graphics, ControlCardStyle.Size.WIDE, 0x303030, x, y, width, height);
                        artwork.addIcon(graphics, x + (width - 28) / 2, y + 9, 28, 30);
                        text(ctx, "Add new", x + 6, y + 45, width - 12, 12, .9F, true, 0xFFFFFFFF);
                        hit(state, x, y, width, height, "Add a remote device in Reader mode", () -> addDevice(ctx));
                    }else device(ctx, state, devices.getCompound(first + idx), x, y, width, height, mouseX, mouseY);
                }
                if(devices.isEmpty()){
                    text(ctx, "No remote devices", 171, 43, 133, 14, .9F, false, 0xFFE3E3E3);
                    GuiControlPreviews.drawFittedWrappedText(graphics, ctx.font(), Component.literal("Use Add new to pair a controller."),
                            171, 62, 133, 27, .75F, 3, 0xFFAAAAAA, false);
                }
            }
            if(maximum > 0){
                text(ctx, "Page " + (state.page + 1) + " / " + (maximum + 1), 24, 169, 90, 8, .65F, false, 0xFFAAAAAA);
                text(ctx, "Scroll to browse devices", 197, 169, 112, 8, .6F, true, 0xFFAAAAAA);
            }
            if(ctx.data().contains("Error")) text(ctx, ctx.data().getString("Error"), 10, 169, 300, 8, .65F, false, 0xFFF28B8B);
            if(state.picker != null){
                graphics.pose().translate(0, 0, 200);
                if(colorAppearance == null) state.picker.render(graphics, ctx.font(), 320, 180, ColorPickerModal.Chrome.DEFAULT, (int)mouseX, (int)mouseY);
                else state.picker.renderTextured(graphics, ctx.font(), 320, 180, colorAppearance, (int)mouseX, (int)mouseY);
            }
        }finally{ graphics.pose().popPose(); }
        if(state.picker == null && CONTENT.contains(mouseX, mouseY)) for(var hit : state.hits){
            if(hit.area().contains(mouseX, mouseY)){
                graphics.renderComponentTooltip(ctx.font(), List.of(Component.literal(hit.tooltip())), ctx.mouseX(), ctx.mouseY());
                break;
            }
        }
    }

    private void device(TabletAppClientContext ctx, State state, CompoundTag device, int x, int y, int width, int height, double mouseX, double mouseY){
        String key = device.getString("Key"), name = device.getString("Name");
        boolean online = device.getBoolean("Online");
        int color = device.contains("Color") ? device.getInt("Color") & 0xFFFFFF : 0x303030;
        if(!state.colorKey.equals(key) && state.colors.containsKey(key) && state.colors.get(key) == color) state.colors.remove(key);
        color = state.colors.getOrDefault(key, color);
        artwork.frame(ctx.graphics(), ControlCardStyle.Size.WIDE, color, x, y, width, height);
        text(ctx, name, x + 6, y + 5, width - 46, 11, .8F, false, 0xFFFFFFFF);
        var eyedropper = new TabletLayout.Rect(x + width - 36, y + 3, 10, 10);
        ColorPickerModal.renderButton(ctx.graphics(), x + width - 35, y + 4, 8, eyedropper.contains(mouseX, mouseY));
        int initial = color;
        hit(state, eyedropper.left(), eyedropper.top(), eyedropper.width(), eyedropper.height(), "Card color", () -> {
            Integer prev = state.colors.get(key);
            state.colorKey = key;
            state.picker = new ColorPickerModal(0xFF000000 | initial, val -> state.colors.put(key, val & 0xFFFFFF),
                    () -> colorDevice(ctx, key, state.colors.getOrDefault(key, initial)), () -> {
                        if(prev == null) state.colors.remove(key); else state.colors.put(key, prev);
                    });
        });
        artwork.deleteIcon(ctx.graphics(), x + width - 24, y + 4, 8);
        artwork.editIcon(ctx.graphics(), x + width - 13, y + 4, 8);
        hit(state, x + width - 25, y + 3, 10, 10, "Remove " + name, () -> removeDevice(ctx, key));
        hit(state, x + width - 14, y + 3, 11, 10, "Rename " + name, () -> ctx.ui().editText("Device name", name, 48,
                val -> renameDevice(ctx, key, val)));
        hit(state, x + 5, y + 4, width - 43, 13, name, null);
        text(ctx, online ? "Online" : "Offline", x + 6, y + 24, width - 12, 10, .75F, false, online ? 0xFF83C993 : 0xFF999999);
        int buttonWidth = (width - 18) / 3;
        for(View view : View.values()){
            int bx = x + 6 + view.ordinal() * (buttonWidth + 3), by = y + 46;
            boolean enabled = online && (!device.contains(view.availability) || device.getBoolean(view.availability));
            artwork.button(ctx.graphics(), bx, by, buttonWidth, 12, view.color);
            if(!enabled) ctx.graphics().fill(bx + 1, by + 1, bx + buttonWidth - 1, by + 11, 0x66000000);
            else if(new TabletLayout.Rect(bx, by, buttonWidth, 12).contains(mouseX, mouseY)) ctx.graphics().fill(bx + 1, by + 1, bx + buttonWidth - 1, by + 11, 0x22FFFFFF);
            text(ctx, view.label, bx + 2, by + 1, buttonWidth - 4, 10, .75F, true, enabled ? 0xFFFFFFFF : 0xFF888888);
            hit(state, bx, by, buttonWidth, 12, enabled ? view.label + " " + name : online ? view.label + " unavailable" : "Device offline",
                    enabled ? () -> openDevice(ctx, key, view) : null);
        }
    }

    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double mouseX, double mouseY, int btn){
        var state = state(raw);
        var bounds = bounds(ctx);
        double x = (mouseX - bounds.left()) / bounds.scaleX(), y = (mouseY - bounds.top()) / bounds.scaleY();
        if(state.picker != null) return state.picker.mouseClicked(x, y, btn);
        if(btn != 0 && !(btn == 1 && ctx.ui().projected())) return false;
        if(!CONTENT.contains(x, y)) return false;
        for(var hit : state.hits){
            if(!hit.area().contains(x, y)) continue;
            if(hit.action() != null) hit.action().run();
            return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double mouseX, double mouseY, double scrollX, double scrollY){
        var state = state(raw);
        if(state.picker != null) return true;
        if(scrollY == 0 || !CANVAS.project(bounds(ctx), CONTENT).contains(mouseX, mouseY)) return false;
        int maximum = ctx.data().getList("Devices", Tag.TAG_COMPOUND).size() / 4;
        state.page = Math.clamp(state.page - (int)Math.signum(scrollY), 0, maximum);
        return maximum > 0;
    }

    @Override public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button, double dx, double dy){
        var picker = state(raw).picker; var bounds = bounds(ctx);
        return picker != null && picker.mouseDragged((x - bounds.left()) / bounds.scaleX(), (y - bounds.top()) / bounds.scaleY(), button);
    }

    @Override public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button){
        var picker = state(raw).picker; var bounds = bounds(ctx);
        return picker != null && picker.mouseReleased((x - bounds.left()) / bounds.scaleX(), (y - bounds.top()) / bounds.scaleY(), button);
    }

    @Override public boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState raw, int key, int scan, int mods){
        var picker = state(raw).picker; return picker != null && picker.keyPressed(key, scan, mods);
    }

    @Override public boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState raw, char ch, int mods){
        var picker = state(raw).picker; return picker != null && picker.charTyped(ch, mods);
    }

    private static void hit(State state, int x, int y, int width, int height, String tooltip, Runnable action){ state.hits.add(new Hit(new TabletLayout.Rect(x, y, width, height), action, tooltip)); }
    private static void text(TabletAppClientContext ctx, String val, int x, int y, int width, int height, float scale, boolean centered, int color){
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), Component.literal(val), x, y, width, height, scale, color, centered);
    }
    private static TabletLayout.Bounds bounds(TabletAppClientContext ctx){ var surface = ctx.surface(); return CANVAS.stretch(surface.left(), surface.top(), surface.width(), surface.height()); }
    private static State state(TabletAppClientState raw){ if(raw instanceof State state) return state; throw new IllegalArgumentException("Device cards received another application's state"); }
}
