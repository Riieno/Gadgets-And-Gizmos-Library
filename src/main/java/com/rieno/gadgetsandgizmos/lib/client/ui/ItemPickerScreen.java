package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Pick registered items and block items with the same searchable icon rows used by graph resource selectors
public final class ItemPickerScreen extends Screen{
    private final Screen parent;
    private final ResourceLocation selected;
    private final Consumer<ResourceLocation> accept;
    private final Predicate<ItemStack> allowed;
    private List<ItemStack> all;
    private List<ItemStack> choices;
    private final Map<ItemStack, ItemSearchQuery.Entry> searchable = new IdentityHashMap<>();
    private EditBox search;
    private int left;
    private int top;
    private int panelWidth;
    private int rows;
    private int scroll;

    // Return one selected registry item to the caller
    public ItemPickerScreen(Screen parent, ResourceLocation selected, Consumer<ResourceLocation> accept){
        this(parent, selected, accept, stack -> true, "Select Item / Block");
    }

    public ItemPickerScreen(Screen parent, ResourceLocation selected, Consumer<ResourceLocation> accept,
                            Predicate<ItemStack> allowed, String title){
        super(Component.literal(title));
        this.parent = parent;
        this.selected = selected;
        this.accept = accept;
        this.allowed = allowed;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Fit the modal to the current window and focus its search field
    @Override protected void init(){
        panelWidth = Math.min(440, width - 24);
        rows = Math.max(1, Math.min(12, (height - 100) / 24));
        left = (width - panelWidth) / 2;
        top = (height - (70 + rows * 24)) / 2;
        all = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).map(ItemStack::new)
                .filter(allowed)
                .sorted(Comparator.comparing(stack -> stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)).toList();
        choices = all;
        searchable.clear();
        for(ItemStack stack : all){
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            searchable.put(stack, ItemSearchQuery.item(id, stack));
        }
        search = new EditBox(font, left + 17, top + 34, panelWidth - 34, 12, Component.literal("Search items"));
        search.setBordered(false);
        search.setTextColor(0xFFEAF3FC);
        search.setTextColorUneditable(0xFF96ACBF);
        search.setMaxLength(128);
        search.setHint(Component.literal("Search name, @mod, #tag / -exclude / | or"));
        search.setResponder(query -> {
            var filter = ItemSearchQuery.compile(query);
            choices = all.stream().filter(stack -> filter.test(searchable.get(stack))).toList();
            scroll = 0;
        });
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    // The modal draws its own background without the vanilla menu blur pass
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
    }

    // Draw item icons, names and IDs before the search widget in the same sharp GUI pass
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        graphics.fill(0, 0, width, height, 0xBD080D13);
        graphics.fill(left, top, left + panelWidth, top + 70 + rows * 24, 0xFF152334);
        graphics.drawString(font, title, left + 12, top + 10, 0xFFCDE7C5, false);
        graphics.drawString(font, "X", left + panelWidth - 20, top + 10, 0xFFEAF3FC, false);
        graphics.fill(left + 12, top + 28, left + panelWidth - 12, top + 48,
                search.isFocused() ? 0xFF8ABD7A : 0xFF304D66);
        graphics.fill(left + 13, top + 29, left + panelWidth - 13, top + 47, 0xFF22394D);
        for(int idx = 0; idx < rows && scroll + idx < choices.size(); idx++){
            ItemStack stack = choices.get(scroll + idx);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            int y = top + 53 + idx * 24;
            boolean hover = mouseX >= left + 10 && mouseX < left + panelWidth - 10 && mouseY >= y && mouseY < y + 23;
            graphics.fill(left + 10, y, left + panelWidth - 10, y + 23, hover ? 0xFF365572 : 0xFF22394D);
            LayeredItemRenderer.renderVisible(graphics, stack, left + 13, y + 3);
            graphics.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(), panelWidth - 60),
                    left + 35, y + 3, id.equals(selected) ? 0xFFA9E68D : 0xFFEAF3FC, false);
            graphics.drawString(font, font.plainSubstrByWidth(id.toString(), panelWidth - 60), left + 35, y + 13, 0xFF96ACBF, false);
        }
        if(choices.isEmpty()) graphics.drawString(font, "No matching items", left + 12, top + 58, 0xFF96ACBF, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // Select a row or close the modal without changing the previous selection
    @Override public boolean mouseClicked(double x, double y, int button){
        if(button != 0) return true;
        if(x < left || x >= left + panelWidth || y < top || y >= top + 70 + rows * 24
                || x >= left + panelWidth - 26 && y < top + 25){ onClose(); return true; }
        if(x >= left + 12 && x < left + panelWidth - 12 && y >= top + 28 && y < top + 48){
            setFocused(search);
            if(search.isMouseOver(x, y)) search.mouseClicked(x, y, button);
            return true;
        }
        int idx = (int) ((y - top - 53) / 24);
        if(y >= top + 53 && idx < rows && scroll + idx < choices.size()){
            accept.accept(BuiltInRegistries.ITEM.getKey(choices.get(scroll + idx).getItem()));
            onClose();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double sx, double sy){
        scroll = Mth.clamp(scroll - (int) Math.signum(sy), 0, Math.max(0, choices.size() - rows));
        return true;
    }

    @Override public void onClose(){ minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen(){ return false; }
}
