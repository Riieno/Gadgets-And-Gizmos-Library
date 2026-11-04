package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedListModal.Region;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

// Share searchable painted cards without owning the host's selection or modal lifecycle
public final class PaintedSelectionGrid<T>{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int COLUMNS = 3;
    public static final int ROWS = 2;
    public static final int VISIBLE_ENTRIES = COLUMNS * ROWS;
    private final Appearance appearance;
    private final Function<T, Component> label;
    private final Function<T, ItemStack> icon;
    private final Function<T, List<Component>> tooltip;
    private final Function<T, ItemSearchQuery.Entry> searchable;
    private final GuiScrollbar scrollbar = new GuiScrollbar(0xFF202020, 0xFFE5BA64);
    private List<T> entries = List.of();
    private List<T> filtered = List.of();
    private Predicate<ItemSearchQuery.Entry> filter = entry -> true;
    private EditBox search;
    private int row;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public PaintedSelectionGrid(Appearance appearance, Function<T, Component> label, Function<T, ItemStack> icon,
                                Function<T, List<Component>> tooltip, Function<T, ItemSearchQuery.Entry> searchable){
        this.appearance = Objects.requireNonNull(appearance);
        this.label = Objects.requireNonNull(label); this.icon = Objects.requireNonNull(icon);
        this.tooltip = Objects.requireNonNull(tooltip); this.searchable = Objects.requireNonNull(searchable);
    }

    // Reset search only when opening a new selection session
    public void open(Font font){
        Region field = appearance.search();
        search = new EditBox(font, field.x() + 3, field.y() + 3, field.width() - 6, field.height() - 6, Component.literal("Search entries"));
        search.setBordered(false); search.setMaxLength(256); search.setTextColor(0xFFFFFFFF);
        search.setHint(Component.literal("Search...")); search.setFocused(true);
        search.setResponder(query -> { filter = ItemSearchQuery.compile(query); row = 0; scrollbar.reset(); refresh(); });
        search.setValue("");
    }

    public void setEntries(List<T> entries){
        List<T> next = List.copyOf(entries);
        if(this.entries.equals(next)) return;
        this.entries = next;
        refresh();
    }

    public List<T> filteredEntries(){ return filtered; }
    public void ensureVisible(Predicate<T> match){
        for(int idx = 0; idx < filtered.size(); idx++){
            if(!match.test(filtered.get(idx))) continue;
            int target = idx / COLUMNS;
            if(target < row) row = target;
            else if(target >= row + ROWS) row = target - ROWS + 1;
            configure();
            return;
        }
    }
    public void setSearchFocused(boolean focused){ if(search != null) search.setFocused(focused); }
    public void mouseReleased(){ scrollbar.mouseReleased(); }
    public void mouseScrolled(double amount){ row = Math.clamp(row - (int)Math.signum(amount), 0, maximum()); configure(); }
    public void mouseDragged(GuiCanvasTransform transform, double y, int button){
        configure();
        if(scrollbar.mouseDragged(transform.localY(y), button)) row = scrollbar.value();
    }
    public void keyPressed(int key, int scan, int modifiers){ if(search != null && search.isFocused()) search.keyPressed(key, scan, modifiers); }
    public void charTyped(char ch, int modifiers){ if(search != null && search.isFocused()) search.charTyped(ch, modifiers); }

    // Render search, cards and tooltips in the same local canvas used for pointer input
    public void render(GuiGraphics graphics, Font font, GuiCanvasTransform transform, int mouseX, int mouseY,
                       Predicate<T> selected, Component empty){
        double x = transform.localX(mouseX), y = transform.localY(mouseY);
        T hovered = null;
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(transform.x(), transform.y(), 0);
            graphics.pose().scale(transform.scale(), transform.scale(), 1);
            if(search != null) search.render(graphics, (int)x, (int)y, 0);
            for(int slot = 0; slot < VISIBLE_ENTRIES && row * COLUMNS + slot < filtered.size(); slot++){
                T entry = filtered.get(row * COLUMNS + slot);
                Region bounds = entryBounds(appearance.grid(), appearance.card().background(), slot);
                drawCard(graphics, font, appearance.card(), bounds, icon.apply(entry), label.apply(entry), selected.test(entry), x, y);
                if(bounds.contains(x, y)) hovered = entry;
            }
            configure();
            scrollbar.render(graphics);
            if(filtered.isEmpty()) GuiControlPreviews.drawFittedText(graphics, font, empty,
                    appearance.grid().x() + 3, appearance.grid().y() + 3, appearance.grid().width() - 6, 16, 0.8F, 0xFFAAAAAA, false);
        }finally{ graphics.pose().popPose(); }
        if(hovered != null) graphics.renderComponentTooltip(font, tooltip.apply(hovered), mouseX, mouseY);
        else if(appearance.search().contains(x, y)) graphics.renderTooltip(font, Component.literal("Search name, ID, @mod or #tag"), mouseX, mouseY);
    }

    public boolean mouseClicked(GuiCanvasTransform transform, double screenX, double screenY, int button, Consumer<T> choose){
        if(button != 0) return true;
        double x = transform.localX(screenX), y = transform.localY(screenY);
        setSearchFocused(appearance.search().contains(x, y));
        if(search != null && search.isFocused()){ search.mouseClicked(x, y, button); return true; }
        configure();
        if(scrollbar.mouseClicked(x, y, button)){ row = scrollbar.value(); return true; }
        for(int slot = 0; slot < VISIBLE_ENTRIES && row * COLUMNS + slot < filtered.size(); slot++){
            if(entryBounds(appearance.grid(), appearance.card().background(), slot).contains(x, y)){
                choose.accept(filtered.get(row * COLUMNS + slot));
                return true;
            }
        }
        return true;
    }

    // Use one geometry for sound and target pickers, including the space beneath each card
    public static Region entryBounds(Region grid, GuiTextureRegion background, int slot){
        int cellWidth = grid.width() / COLUMNS, cellHeight = grid.height() / ROWS;
        return new Region(grid.x() + slot % COLUMNS * cellWidth + (cellWidth - background.width()) / 2,
                grid.y() + slot / COLUMNS * cellHeight, background.width(), background.height());
    }

    public static void drawCard(GuiGraphics graphics, Font font, CardAppearance appearance, Region bounds,
                                ItemStack icon, Component label, boolean selected, double mouseX, double mouseY){
        appearance.background().draw(graphics, bounds.x(), bounds.y());
        if(bounds.contains(mouseX, mouseY)) graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, 0x18FFFFFF);
        Region check = at(bounds, appearance.checkbox());
        if(selected) appearance.tick().draw(graphics, check.x(), check.y(), check.width(), check.height());
        Region image = at(bounds, appearance.icon());
        LayeredItemRenderer.renderVisible(graphics, icon, image.x(), image.y(), image.width());
        Region text = at(bounds, appearance.label());
        var lines = font.split(label, (int)(text.width() / 0.6F));
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(text.x(), text.y(), 1);
            graphics.pose().scale(0.6F, 0.6F, 1);
            for(int idx = 0; idx < Math.min(2, lines.size()); idx++) graphics.drawString(font, lines.get(idx), 0, idx * font.lineHeight, 0xFFFFFFFF, false);
        }finally{ graphics.pose().popPose(); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private void refresh(){
        filtered = entries.stream().filter(entry -> filter.test(searchable.apply(entry))).toList();
        row = Math.clamp(row, 0, maximum());
    }
    private int maximum(){ return Math.max(0, (filtered.size() + COLUMNS - 1) / COLUMNS - ROWS); }
    private void configure(){
        Region track = appearance.scrollbar();
        scrollbar.configure(track.x(), track.y(), track.width(), track.height(), maximum(), row);
    }
    private static Region at(Region parent, Region child){ return new Region(parent.x() + child.x(), parent.y() + child.y(), child.width(), child.height()); }

    public record CardAppearance(GuiTextureRegion background, GuiTextureRegion tick, Region checkbox, Region icon, Region label){
        public CardAppearance{
            Objects.requireNonNull(background); Objects.requireNonNull(tick);
            for(Region region : List.of(checkbox, icon, label)){
                if(region.x() < 0 || region.y() < 0 || region.right() > background.width() || region.bottom() > background.height()){
                    throw new IllegalArgumentException("Card controls must fit inside their background");
                }
            }
        }
    }
    public record Appearance(CardAppearance card, Region grid, Region search, Region scrollbar){
        public Appearance{
            Objects.requireNonNull(card); Objects.requireNonNull(grid); Objects.requireNonNull(search); Objects.requireNonNull(scrollbar);
            if(grid.width() / COLUMNS < card.background().width() || grid.height() / ROWS < card.background().height()
                    || search.width() <= 6 || search.height() <= 6 || scrollbar.height() <= 12){
                throw new IllegalArgumentException("Selection grid must fit six cards and usable search and scroll controls");
            }
        }
    }
}
