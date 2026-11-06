package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;

// Share the controller frequency frame, registry picker and inventory ghost choices
public final class LinkFrequencyModal{
    private final GuiTextureRegion background;
    private final BiConsumer<ItemStack, ItemStack> changed;
    private final ItemStack[] frequency;
    private final GuiTextureRegion redSlot, blueSlot;
    private final PlayerInventoryCursor inventory = PlayerInventoryCursor.current();
    private PaintedSelectionModal picker;
    private int selected;
    private int left, top;
    private boolean open = true;
    private String notice = "";

    public LinkFrequencyModal(GuiTextureRegion background, ItemStack first, ItemStack second, int selected,
                              BiConsumer<ItemStack, ItemStack> changed){
        this(background, null, null, first, second, selected, changed);
    }

    public LinkFrequencyModal(GuiTextureRegion background, GuiTextureRegion redSlot, GuiTextureRegion blueSlot,
                              ItemStack first, ItemStack second, int selected, BiConsumer<ItemStack, ItemStack> changed){
        this.background = background;
        this.redSlot = redSlot; this.blueSlot = blueSlot;
        this.frequency = new ItemStack[]{first.copyWithCount(1), second.copyWithCount(1)};
        this.selected = Math.clamp(selected, 0, 1);
        this.changed = changed;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep the original ACC dialog artwork and title placement for every host
    public static void frame(GuiGraphics graphics, Font font, GuiTextureRegion background, int x, int y, String title, int color){
        background.draw(graphics, x, y);
        GuiControlPreviews.drawFittedText(graphics, font, Component.literal(title), x + 11, y + 3, 150, 9, .8F, color, true);
    }

    // Use the same registered item choices and selection logic as ACC graph frequency slots
    public static PaintedSelectionModal itemPicker(Font font, ItemStack selected, java.util.function.Consumer<ItemStack> accept, Runnable closed){
        var entries = ItemPickerScreen.registryEntries();
        return new PaintedSelectionModal(ItemPickerScreen.defaultAppearance(), font, Component.translatable("gadgetsngizmos.text.select_frequency_item"), entries,
                ItemPickerScreen.registrySelection(entries, selected), false, ids -> {
            if(!ids.isEmpty()) entries.stream().filter(entry -> entry.id().equals(ids.getFirst())).findFirst()
                    .ifPresent(entry -> accept.accept(entry.icon().copyWithCount(1)));
        }, closed);
    }

    public boolean isOpen(){ return open; }

    public void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY){
        left = (width - 176) / 2; top = (height - 155) / 2;
        graphics.fill(0, 0, width, height, 0x99000000);
        frame(graphics, font, background, left, top, "Redstone Link Frequency", 0xFF404040);
        for(int idx = 0; idx < 2; idx++){
            int x = left + 60 + idx * 18, y = top + 27;
            var slot = idx == 0 ? redSlot : blueSlot;
            if(slot != null) slot.draw(graphics, x - 1, y - 1, 18, 18);
            else graphics.fill(x - 1, y - 1, x + 17, y + 17, idx == 0 ? 0xFF963A48 : 0xFF4164AB);
            if(!frequency[idx].isEmpty()) graphics.renderItem(frequency[idx], x, y);
        }
        graphics.drawString(font, "Inventory", left + 7, top + 61, 0xFF404040, false);
        var player = Minecraft.getInstance().player;
        if(player != null) for(int idx = 0; idx < 36; idx++){
            int slot = idx < 27 ? idx + 9 : idx - 27;
            int x = left + 7 + idx % 9 * 18, y = top + 73 + idx / 9 * 18 + (idx >= 27 ? 4 : 0);
            ItemStack stack = player.getInventory().getItem(slot);
            if(!stack.isEmpty()){
                graphics.renderItem(stack, x, y);
                graphics.renderItemDecorations(font, stack, x, y);
                if(mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16 && picker == null && carried().isEmpty())
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
        if(!notice.isBlank()) GuiControlPreviews.drawFittedText(graphics, font, Component.literal(notice), left + 5, top + 49, 166, 10, .7F, 0xFFFF8080, true);
        if(picker == null && !carried().isEmpty() && !inventory.renderedByHost()){
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            try{
                graphics.renderItem(carried(), mouseX - 8, mouseY - 8);
                graphics.renderItemDecorations(font, carried(), mouseX - 8, mouseY - 8);
            }finally{ graphics.pose().popPose(); }
        }
        if(picker != null){
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            try{ picker.render(graphics, font, width, height, mouseX, mouseY); }
            finally{ graphics.pose().popPose(); }
        }
    }

    public boolean mouseClicked(Font font, int width, int height, double x, double y, int button){
        if(picker != null){ picker.mouseClicked(width, height, x, y, button); return true; }
        for(int idx = 0; idx < 2; idx++) if(inside(x, y, left + 59 + idx * 18, top + 26, 18, 18)){
            selected = idx;
            if(!carried().isEmpty()) set(carried());
            else if(button == 1) set(ItemStack.EMPTY);
            else picker = itemPicker(font, frequency[idx], this::set, () -> picker = null);
            return true;
        }
        if(inside(x, y, left + 98, top + 26, 18, 18)){
            frequency[0] = frequency[1] = ItemStack.EMPTY;
            changed.accept(ItemStack.EMPTY, ItemStack.EMPTY);
            return true;
        }
        var player = Minecraft.getInstance().player;
        if(player != null) for(int idx = 0; idx < 36; idx++){
            int slot = idx < 27 ? idx + 9 : idx - 27;
            if(inside(x, y, left + 7 + idx % 9 * 18, top + 73 + idx / 9 * 18 + (idx >= 27 ? 4 : 0), 16, 16)){
                if(inventory != null) inventory.click(slot, button);
                return true;
            }
        }
        if(!inside(x, y, left, top, 176, 155)) close();
        return true;
    }

    private void set(ItemStack stack){
        frequency[selected] = stack.copyWithCount(1);
        changed.accept(frequency[0].copy(), frequency[1].copy());
    }
    public boolean keyPressed(int key, int scan, int modifiers){
        if(picker != null) return picker.keyPressed(key, scan, modifiers);
        if(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) close();
        return true;
    }
    public boolean charTyped(char ch, int modifiers){ return picker != null && picker.charTyped(ch, modifiers); }
    public boolean mouseScrolled(double amount){ return picker != null && picker.mouseScrolled(amount); }
    public boolean mouseDragged(int width, int height, double y, int button){ return picker != null && picker.mouseDragged(width, height, y, button); }
    public boolean mouseReleased(){ return picker != null && picker.mouseReleased(); }
    public boolean mouseReleased(double x, double y, int button){
        if(picker != null) return picker.mouseReleased();
        if(!carried().isEmpty() && (button == 0 || button == 1)) for(int idx = 0; idx < 2; idx++){
            if(inside(x, y, left + 59 + idx * 18, top + 26, 18, 18)){ selected = idx; set(carried()); return true; }
        }
        return true;
    }
    private ItemStack carried(){ return inventory == null ? ItemStack.EMPTY : inventory.carried(); }
    private void close(){
        if(inventory == null || inventory.returnCarried()) open = false;
        else notice = "Return held items to inventory first";
    }
    private static boolean inside(double x, double y, int left, int top, int width, int height){
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
