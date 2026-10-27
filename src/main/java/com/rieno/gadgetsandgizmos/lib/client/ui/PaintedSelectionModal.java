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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

// Reuse the painted six-card selector for registry items and caller-defined choices
public final class PaintedSelectionModal implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final Appearance appearance;
    private final Component title;
    private final PaintedSelectionGrid<Entry> grid;
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private final boolean multiple;
    private final Consumer<List<String>> accepted;
    private final Runnable closed;
    private boolean open = true;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public PaintedSelectionModal(Appearance appearance, Font font, Component title, List<Entry> entries,
                                  Collection<String> initial, boolean multiple, Consumer<List<String>> accepted, Runnable closed){
        this.appearance = Objects.requireNonNull(appearance);
        this.title = Objects.requireNonNull(title);
        this.multiple = multiple;
        this.accepted = Objects.requireNonNull(accepted);
        this.closed = Objects.requireNonNull(closed);
        initial.stream().filter(id -> id != null && !id.isBlank()).forEach(selected::add);
        if(!multiple && selected.size() > 1) throw new IllegalArgumentException("Single selectors accept one initial entry");
        grid = new PaintedSelectionGrid<>(appearance.grid(), Entry::label, Entry::icon, Entry::tooltip, Entry::searchable);
        grid.open(font);
        grid.setEntries(entries);
        grid.ensureVisible(entry -> selected.contains(entry.id()));
    }

    public boolean isOpen(){ return open; }
    public List<String> selectedIds(){ return List.copyOf(selected); }

    // Refresh available choices while retaining the search and any saved selections
    public void setEntries(List<Entry> entries){ grid.setEntries(entries); }

    public void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY){
        if(!open) return;
        var transform = transform(width, height);
        double x = transform.localX(mouseX), y = transform.localY(mouseY);
        graphics.fill(0, 0, width, height, 0xA8000000);
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(transform.x(), transform.y(), 0);
            graphics.pose().scale(transform.scale(), transform.scale(), 1);
            appearance.panel().draw(graphics, 0, 0);
            text(graphics, font, title, appearance.title(), 1, 0xFFFFFFFF);
            text(graphics, font, Component.literal("x"), appearance.close(), 0.8F, 0xFFFFFFFF);
            Region accept = appearance.accept();
            appearance.grid().card().tick().draw(graphics, accept.x(), accept.y(), accept.width(), accept.height());
            text(graphics, font, Component.literal(selected.size() + " selected  |  @mod  #tag  -exclude  | alternatives"), appearance.footer(), 0.6F, 0xFF303030);
            for(Region action : List.of(appearance.accept(), appearance.close())){
                if(action.contains(x, y)) graphics.fill(action.x(), action.y(), action.right(), action.bottom(), 0x22FFFFFF);
            }
        }finally{ graphics.pose().popPose(); }
        grid.render(graphics, font, transform, mouseX, mouseY, entry -> selected.contains(entry.id()), Component.literal("No matching entries"));
        if(appearance.accept().contains(x, y)) graphics.renderTooltip(font, Component.literal("Accept selection"), mouseX, mouseY);
        else if(appearance.close().contains(x, y)) graphics.renderTooltip(font, Component.literal("Cancel"), mouseX, mouseY);
    }

    public boolean mouseClicked(int width, int height, double x, double y, int button){
        if(!open) return false;
        if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        var transform = transform(width, height);
        double localX = transform.localX(x), localY = transform.localY(y);
        if(appearance.close().contains(localX, localY)){ close(); return true; }
        if(appearance.accept().contains(localX, localY)){ accept(); return true; }
        return grid.mouseClicked(transform, x, y, button, entry -> {
            if(multiple){
                if(!selected.remove(entry.id())) selected.add(entry.id());
            }else{
                selected.clear(); selected.add(entry.id());
            }
        });
    }

    public boolean mouseDragged(int width, int height, double y, int button){
        if(open) grid.mouseDragged(transform(width, height), y, button);
        return open;
    }
    public boolean mouseReleased(){ grid.mouseReleased(); return open; }
    public boolean mouseScrolled(double amount){ if(open) grid.mouseScrolled(amount); return open; }
    public boolean charTyped(char ch, int modifiers){ if(open) grid.charTyped(ch, modifiers); return open; }
    public boolean keyPressed(int key, int scan, int modifiers){
        if(!open) return false;
        if(key == GLFW.GLFW_KEY_ESCAPE) close();
        else if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) accept();
        else if(key == GLFW.GLFW_KEY_TAB) grid.setSearchFocused(true);
        else grid.keyPressed(key, scan, modifiers);
        return true;
    }

    public void accept(){
        if(!open) return;
        open = false;
        grid.mouseReleased();
        try{ accepted.accept(selectedIds()); }
        finally{ closed.run(); }
    }

    @Override public void close(){
        if(!open) return;
        open = false;
        grid.mouseReleased();
        closed.run();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private GuiCanvasTransform transform(int width, int height){
        return GuiCanvasTransform.fit(appearance.panel().width(), appearance.panel().height(), width, height, 8, 2);
    }

    private static void text(GuiGraphics graphics, Font font, Component val, Region bounds, float scale, int color){
        GuiControlPreviews.drawFittedText(graphics, font, val, bounds.x(), bounds.y(), bounds.width(), bounds.height(), scale, color, true);
    }

    public record Entry(String id, Component label, ItemStack icon, ItemSearchQuery.Entry searchable, List<Component> tooltip){
        public Entry{
            Objects.requireNonNull(id); Objects.requireNonNull(label); Objects.requireNonNull(searchable);
            icon = Objects.requireNonNull(icon).copy();
            tooltip = List.copyOf(tooltip);
        }

        // Preserve a caller's identity while searching the item's registry ID, name, mod and tags
        public static Entry item(String id, ItemStack stack){
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return new Entry(id, stack.getHoverName(), stack, ItemSearchQuery.item(item, stack),
                    List.of(stack.getHoverName(), Component.literal(item.toString())));
        }
    }

    public record Appearance(GuiTextureRegion panel, PaintedSelectionGrid.Appearance grid, Region title, Region footer, Region accept, Region close){
        public Appearance{
            Objects.requireNonNull(panel); Objects.requireNonNull(grid);
            for(Region region : List.of(grid.grid(), grid.search(), grid.scrollbar(), title, footer, accept, close)){
                if(region.x() < 0 || region.y() < 0 || region.right() > panel.width() || region.bottom() > panel.height()){
                    throw new IllegalArgumentException("Selection controls must fit inside their panel");
                }
            }
        }

        // Use the established picker artwork in a namespace owned by the caller
        public static Appearance standard(String namespace){
            var panel = new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath(namespace, "textures/gui/modals/generic_select_with_search_modal.png"), 280, 240, 0, 0, 280, 240);
            var entry = new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath(namespace, "textures/gui/modals/select_button_square.png"), 72, 66, 0, 0, 72, 66);
            var tick = new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath(namespace, "textures/gui/template.png"), 508, 268, 228, 163, 9, 9);
            var grid = new PaintedSelectionGrid.Appearance(new PaintedSelectionGrid.CardAppearance(entry, tick,
                    new Region(6, 12, 8, 8), new Region(32, 6, 16, 16), new Region(9, 44, 54, 14)),
                    new Region(8, 47, 261, 172), new Region(133, 24, 139, 14), new Region(271, 47, 3, 172));
            return new Appearance(panel, grid, new Region(5, 1, 257, 12), new Region(5, 228, 251, 10), new Region(264, 228, 9, 9), new Region(264, 1, 10, 12));
        }
    }
}
