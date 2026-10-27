package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.sound.SoundPreview;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedListModal.Region;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

// A painted sound grid with staged selection, scoped searches and exclusive local previews
public final class SoundPickerModal implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int COLUMNS = 3;
    private static final int ROWS = 2;
    private static final int THUMB_HEIGHT = 12;
    private final Appearance appearance;
    private final List<SoundCatalog.Entry> entries;
    private final SoundPreview preview;
    private final Consumer<ResourceLocation> accepted;
    private final Runnable closed;
    private final EditBox search;
    private final ListScrollState scroll = new ListScrollState();
    private List<SoundCatalog.Entry> filtered;
    private ResourceLocation selected;
    private boolean open = true;
    private boolean dragging;
    private double dragOffset;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Open the registered sound catalog with a preview owned by this modal
    public SoundPickerModal(Appearance appearance, Font font, ResourceLocation initial,
                            Consumer<ResourceLocation> accepted, Runnable closed){
        this(appearance, font, SoundCatalog.registered(Minecraft.getInstance().getSoundManager()),
                new SoundPreview(Minecraft.getInstance().getSoundManager()), initial, accepted, closed);
    }

    // Allow callers to provide a restricted catalog and their own preview manager
    public SoundPickerModal(Appearance appearance, Font font, List<SoundCatalog.Entry> entries, SoundPreview preview,
                            ResourceLocation initial, Consumer<ResourceLocation> accepted, Runnable closed){
        this.appearance = Objects.requireNonNull(appearance);
        this.entries = List.copyOf(entries);
        this.preview = Objects.requireNonNull(preview);
        this.accepted = Objects.requireNonNull(accepted);
        this.closed = Objects.requireNonNull(closed);
        selected = initial;
        filtered = this.entries;
        Region field = appearance.layout().search();
        search = new EditBox(font, field.x() + 3, field.y() + 3, field.width() - 6, field.height() - 6,
                Component.literal("Search sounds by name, @mod or #tag"));
        search.setBordered(false);
        search.setMaxLength(256);
        search.setTextColor(0xFFFFFFFF);
        search.setHint(Component.literal("Search..."));
        search.setFocused(true);
        search.setResponder(this::filter);
        configureScroll();
        for(int idx = 0; idx < filtered.size(); idx++){
            if(filtered.get(idx).id().equals(initial)){ scroll.ensureVisible(idx / COLUMNS); break; }
        }
    }

    public boolean isOpen(){ return open; }

    // Draw the complete artwork and controls with one shared canvas transform
    public void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY, float partialTick){
        if(!open) return;
        var transform = transform(width, height);
        double x = transform.localX(mouseX), y = transform.localY(mouseY);
        Layout layout = appearance.layout();
        graphics.fill(0, 0, width, height, 0xA8000000);
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(transform.x(), transform.y(), 0);
            graphics.pose().scale(transform.scale(), transform.scale(), 1);
            appearance.panel().draw(graphics, 0, 0);
            text(graphics, font, Component.literal("Sound Picker"), layout.title(), 1, 0xFFFFFFFF);
            search.render(graphics, (int)x, (int)y, partialTick);
            for(int slot = 0; slot < COLUMNS * ROWS; slot++){
                int idx = scroll.offset() * COLUMNS + slot;
                if(idx >= filtered.size()) break;
                drawEntry(graphics, font, filtered.get(idx), slot, x, y);
            }
            if(filtered.isEmpty()) text(graphics, font, Component.literal("No matching sounds"), layout.grid(), 1, 0xFFAAAAAA);
            Region track = layout.scrollbar();
            if(scroll.maximumOffset() > 0){
                graphics.fill(track.x(), track.y(), track.right(), track.bottom(), 0xFF202020);
                graphics.fill(track.x(), thumbTop(), track.right(), thumbTop() + THUMB_HEIGHT, 0xFFE5BA64);
            }
            String status = filtered.size() + " sounds  |  @mod  #tag  -exclude  | alternatives";
            text(graphics, font, Component.literal(status), layout.footer(), 0.6F, 0xFF303030);
            appearance.tick().draw(graphics, layout.accept().x(), layout.accept().y(), layout.accept().width(), layout.accept().height());
            text(graphics, font, Component.literal("x"), layout.cancel(), 0.8F, 0xFFFFFFFF);
            if(layout.accept().contains(x, y) || layout.cancel().contains(x, y)){
                Region action = layout.accept().contains(x, y) ? layout.accept() : layout.cancel();
                graphics.fill(action.x(), action.y(), action.right(), action.bottom(), 0x22FFFFFF);
            }
        }finally{ graphics.pose().popPose(); }
        List<Component> tooltip = tooltip(x, y);
        if(!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    // Consume every click so the graph behind the modal cannot receive input
    public boolean mouseClicked(int width, int height, double mouseX, double mouseY, int button){
        if(!open) return false;
        if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        var transform = transform(width, height);
        double x = transform.localX(mouseX), y = transform.localY(mouseY);
        Layout layout = appearance.layout();
        if(layout.cancel().contains(x, y)){ close(); return true; }
        if(layout.accept().contains(x, y)){ accept(); return true; }
        search.setFocused(layout.search().contains(x, y));
        if(search.isFocused()){ search.mouseClicked(x, y, button); return true; }
        if(layout.scrollbar().contains(x, y) && scroll.maximumOffset() > 0){
            dragging = true;
            dragOffset = y >= thumbTop() && y < thumbTop() + THUMB_HEIGHT ? y - thumbTop() : THUMB_HEIGHT / 2.0;
            dragTo(y);
            return true;
        }
        for(int slot = 0; slot < COLUMNS * ROWS; slot++){
            int idx = scroll.offset() * COLUMNS + slot;
            if(idx >= filtered.size()) break;
            SoundCatalog.Entry entry = filtered.get(idx);
            if(previewBounds(slot).contains(x, y)){ preview.toggle(entry.id()); return true; }
            if(entryBounds(slot).contains(x, y)){ selected = entry.id(); return true; }
        }
        return true;
    }

    public boolean mouseDragged(int width, int height, double mouseY, int button){
        if(dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragTo(transform(width, height).localY(mouseY));
        return open;
    }

    public boolean mouseReleased(){ dragging = false; return open; }

    public boolean mouseScrolled(double amount){
        if(!open) return false;
        scroll.scrollBy(-(int)Math.signum(amount));
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers){
        if(!open) return false;
        if(key == GLFW.GLFW_KEY_ESCAPE) close();
        else if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) accept();
        else if(key == GLFW.GLFW_KEY_TAB) search.setFocused(true);
        else if(search.isFocused()) search.keyPressed(key, scan, modifiers);
        return true;
    }

    public boolean charTyped(char ch, int modifiers){
        if(!open) return false;
        if(search.isFocused()) search.charTyped(ch, modifiers);
        return true;
    }

    public void accept(){
        if(!open) return;
        try{
            if(selected != null) accepted.accept(selected);
        }finally{ close(); }
    }

    // Stop this modal's preview on accept, cancel or removal of the owning screen
    @Override public void close(){
        if(!open) return;
        open = false;
        preview.close();
        closed.run();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private void filter(String query){
        var predicate = ItemSearchQuery.compile(query);
        filtered = entries.stream().filter(entry -> predicate.test(entry.searchable())).toList();
        dragging = false;
        configureScroll();
        scroll.scrollToFraction(0);
    }

    private void configureScroll(){ scroll.configure((filtered.size() + COLUMNS - 1) / COLUMNS, 1, ROWS); }

    private GuiCanvasTransform transform(int width, int height){
        return GuiCanvasTransform.fit(appearance.panel().width(), appearance.panel().height(), width, height, 8, 2);
    }

    private Region entryBounds(int slot){
        return PaintedSelectionGrid.entryBounds(appearance.layout().grid(), appearance.entry(), slot);
    }

    private Region previewBounds(int slot){
        Region entry = entryBounds(slot);
        return new Region(entry.x(), entry.bottom() + 2, entry.width(), 12);
    }

    private int thumbTop(){
        Region track = appearance.layout().scrollbar();
        return track.y() + (int)Math.round(scroll.fraction() * (track.height() - THUMB_HEIGHT));
    }

    private void dragTo(double y){
        Region track = appearance.layout().scrollbar();
        scroll.scrollToFraction((y - track.y() - dragOffset) / (track.height() - THUMB_HEIGHT));
    }

    private void drawEntry(GuiGraphics graphics, Font font, SoundCatalog.Entry entry, int slot, double x, double y){
        Region bounds = entryBounds(slot);
        EntryLayout layout = appearance.layout().entry();
        PaintedSelectionGrid.drawCard(graphics, font, new PaintedSelectionGrid.CardAppearance(appearance.entry(), appearance.tick(),
                layout.checkbox(), layout.icon(), layout.label()), bounds, entry.icon(), entry.name(), entry.id().equals(selected), x, y);
        Region button = previewBounds(slot);
        boolean playing = preview.isPlaying(entry.id());
        graphics.fill(button.x(), button.y(), button.right(), button.bottom(), button.contains(x, y) ? 0xFF555555 : 0xFF383838);
        graphics.renderOutline(button.x(), button.y(), button.width(), button.height(), playing ? 0xFFE5BA64 : 0xFF777777);
        text(graphics, font, Component.literal(playing ? "Stop" : "Preview"), button, 0.8F, 0xFFFFFFFF);
    }

    private List<Component> tooltip(double x, double y){
        if(appearance.layout().search().contains(x, y)) return List.of(Component.literal("Name or ID, @mod, #sound_event_tag"),
                Component.literal("Combine terms, \"quoted phrases\", -exclude, or | alternatives"));
        if(appearance.layout().accept().contains(x, y)) return List.of(Component.literal("Select sound"));
        if(appearance.layout().cancel().contains(x, y)) return List.of(Component.literal("Cancel"));
        for(int slot = 0; slot < COLUMNS * ROWS; slot++){
            int idx = scroll.offset() * COLUMNS + slot;
            if(idx >= filtered.size()) break;
            if(!entryBounds(slot).contains(x, y)) continue;
            SoundCatalog.Entry entry = filtered.get(idx);
            List<Component> lines = new ArrayList<>(List.of(entry.name(), Component.literal(entry.id().toString()),
                    Component.literal(entry.searchable().mod())));
            for(String tag : entry.searchable().tags()) lines.add(Component.literal("#" + tag));
            return lines;
        }
        return List.of();
    }

    private static Region at(Region parent, Region child){
        return new Region(parent.x() + child.x(), parent.y() + child.y(), child.width(), child.height());
    }

    private static void text(GuiGraphics graphics, Font font, Component val, Region bounds, float maxScale, int color){
        float scale = Math.min(maxScale, Math.min(bounds.width() / (float)Math.max(1, font.width(val)), bounds.height() / (float)font.lineHeight));
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(bounds.x() + bounds.width() / 2F, bounds.y() + bounds.height() / 2F, 1);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, val, -font.width(val) / 2, -font.lineHeight / 2, color, false);
        }finally{ graphics.pose().popPose(); }
    }

    public record EntryLayout(Region checkbox, Region icon, Region label){}
    public record Layout(Region title, Region search, Region grid, Region scrollbar, Region footer,
                         Region accept, Region cancel, EntryLayout entry){}

    public record Appearance(GuiTextureRegion panel, GuiTextureRegion entry, GuiTextureRegion tick, Layout layout){
        public Appearance{
            Objects.requireNonNull(panel); Objects.requireNonNull(entry); Objects.requireNonNull(tick); Objects.requireNonNull(layout);
            for(Region region : List.of(layout.title(), layout.search(), layout.grid(), layout.scrollbar(), layout.footer(), layout.accept(), layout.cancel())){
                if(region.x() < 0 || region.y() < 0 || region.right() > panel.width() || region.bottom() > panel.height()){
                    throw new IllegalArgumentException("Sound picker controls must fit inside the panel");
                }
            }
            if(layout.grid().width() / COLUMNS < entry.width() || layout.grid().height() / ROWS < entry.height() + 14
                    || layout.scrollbar().height() <= THUMB_HEIGHT || layout.search().width() <= 6 || layout.search().height() <= 6){
                throw new IllegalArgumentException("Sound picker must fit six entries, previews and usable search and scroll controls");
            }
            Objects.requireNonNull(layout.entry());
            for(Region region : List.of(layout.entry().checkbox(), layout.entry().icon(), layout.entry().label())){
                if(region.x() < 0 || region.y() < 0 || region.right() > entry.width() || region.bottom() > entry.height()){
                    throw new IllegalArgumentException("Sound entry controls must fit inside the entry background");
                }
            }
        }
    }
}
