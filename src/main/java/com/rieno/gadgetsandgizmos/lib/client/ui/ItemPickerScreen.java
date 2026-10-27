package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Share the painted selector across registry item, block, container and inventory pickers
public final class ItemPickerScreen extends Screen{
    private static PaintedSelectionModal.Appearance defaultAppearance = PaintedSelectionModal.Appearance.standard("gadgetsngizmos");
    private final Screen parent;
    private final ResourceLocation selected;
    private final Consumer<ResourceLocation> accept;
    private final Predicate<ItemStack> allowed;
    private List<ItemStack> inventory;
    private ItemStack initialStack = ItemStack.EMPTY;
    private Consumer<ItemStack> acceptStack;
    private PaintedSelectionModal modal;
    private final Map<String, ItemStack> stacks = new LinkedHashMap<>();

    public ItemPickerScreen(Screen parent, ResourceLocation selected, Consumer<ResourceLocation> accept){
        this(parent, selected, accept, stack -> true, "Select Item / Block");
    }

    public ItemPickerScreen(Screen parent, ResourceLocation selected, Consumer<ResourceLocation> accept,
                            Predicate<ItemStack> allowed, String title){
        super(Component.literal(title));
        this.parent = parent;
        this.selected = selected;
        this.accept = Objects.requireNonNull(accept);
        this.allowed = Objects.requireNonNull(allowed);
    }

    private ItemPickerScreen(Screen parent, List<ItemStack> inventory, ItemStack selected, Consumer<ItemStack> accept, String title){
        this(parent, null, ignored -> {}, stack -> true, title);
        this.inventory = inventory.stream().filter(stack -> !stack.isEmpty()).map(stack -> stack.copyWithCount(1)).toList();
        initialStack = selected.copy();
        acceptStack = Objects.requireNonNull(accept);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Let the owning mod supply its established picker artwork for every shared item screen
    public static void setDefaultAppearance(PaintedSelectionModal.Appearance appearance){ defaultAppearance = Objects.requireNonNull(appearance); }
    public static PaintedSelectionModal.Appearance defaultAppearance(){ return defaultAppearance; }

    // Keep inventory-only restrictions and item components when choosing frequency items
    public static ItemPickerScreen inventory(Screen parent, List<ItemStack> choices, ItemStack selected, Consumer<ItemStack> accept, String title){
        return new ItemPickerScreen(parent, choices, selected, accept, title);
    }

    // Share inventory-backed choices with embedded hosts while keeping component variants separate
    public static List<PaintedSelectionModal.Entry> inventoryEntries(List<ItemStack> choices){
        List<ItemStack> unique = new ArrayList<>();
        for(ItemStack stack : choices){
            if(!stack.isEmpty() && unique.stream().noneMatch(prev -> ItemStack.isSameItemSameComponents(prev, stack))) unique.add(stack.copyWithCount(1));
        }
        unique.sort(Comparator.comparing(stack -> stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        List<PaintedSelectionModal.Entry> entries = new ArrayList<>();
        for(int idx = 0; idx < unique.size(); idx++) entries.add(PaintedSelectionModal.Entry.item("inventory:" + idx, unique.get(idx)));
        return List.copyOf(entries);
    }

    public static List<String> inventorySelection(List<PaintedSelectionModal.Entry> entries, ItemStack selected){
        return entries.stream().filter(entry -> ItemStack.isSameItemSameComponents(entry.icon(), selected))
                .map(PaintedSelectionModal.Entry::id).limit(1).toList();
    }

    // Keep a single modal alive across window resizes so pending edits and searches survive
    @Override protected void init(){
        if(modal != null) return;
        List<PaintedSelectionModal.Entry> entries = inventory == null ? BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
                .map(ItemStack::new).filter(allowed)
                .sorted(Comparator.comparing(stack -> stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER))
                .map(stack -> PaintedSelectionModal.Entry.item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack)).toList()
                : inventoryEntries(inventory);
        entries.forEach(entry -> stacks.put(entry.id(), entry.icon().copyWithCount(1)));
        List<String> initial = inventory == null ? selected == null ? List.of() : List.of(selected.toString()) : inventorySelection(entries, initialStack);
        modal = new PaintedSelectionModal(defaultAppearance, font, title, entries, initial, false, ids -> {
            if(ids.isEmpty()) return;
            ItemStack stack = stacks.get(ids.getFirst());
            if(stack == null) return;
            if(acceptStack != null) acceptStack.accept(stack.copyWithCount(1));
            else accept.accept(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }, () -> {});
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){}
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){ modal.render(graphics, font, width, height, mouseX, mouseY); }

    @Override public boolean mouseClicked(double x, double y, int button){
        modal.mouseClicked(width, height, x, y, button);
        returnIfClosed();
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy){ return modal.mouseDragged(width, height, y, button); }
    @Override public boolean mouseReleased(double x, double y, int button){ return modal.mouseReleased(); }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy){ return modal.mouseScrolled(sy); }
    @Override public boolean charTyped(char ch, int modifiers){ return modal.charTyped(ch, modifiers); }
    @Override public boolean keyPressed(int key, int scan, int modifiers){
        modal.keyPressed(key, scan, modifiers);
        returnIfClosed();
        return true;
    }
    @Override public void onClose(){ if(modal != null) modal.close(); minecraft.setScreen(parent); }
    @Override public void removed(){ if(modal != null) modal.close(); super.removed(); }
    @Override public boolean isPauseScreen(){ return false; }

    private void returnIfClosed(){ if(!modal.isOpen()) minecraft.setScreen(parent); }
}
