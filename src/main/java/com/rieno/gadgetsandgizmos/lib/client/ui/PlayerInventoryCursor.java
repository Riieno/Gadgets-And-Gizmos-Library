package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// Use the active server menu for real inventory pickup while ghost targets only copy its carried stack
public final class PlayerInventoryCursor{
    private final Player player;
    private final AbstractContainerMenu menu;
    private int source = -1;

    private PlayerInventoryCursor(Player player, AbstractContainerMenu menu){ this.player = player; this.menu = menu; }

    public static PlayerInventoryCursor current(){
        var player = Minecraft.getInstance().player;
        return player == null ? null : new PlayerInventoryCursor(player, player.containerMenu);
    }

    public ItemStack carried(){ return menu.getCarried(); }

    public boolean click(int inventorySlot, int button){
        if(button != 0 && button != 1) return false;
        for(Slot slot : menu.slots) if(slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot){
            if(carried().isEmpty()) source = slot.index;
            return click(slot, button);
        }
        return false;
    }

    // Return held items before dismissing a modal, preserving the original slot when possible
    public boolean returnCarried(){
        if(carried().isEmpty()) return true;
        if(source >= 0 && source < menu.slots.size()){
            var slot = menu.slots.get(source);
            if(accepts(slot, true) || accepts(slot, false)) click(slot, 0);
        }
        for(boolean empty : new boolean[]{false, true}) for(Slot slot : menu.slots){
            if(carried().isEmpty()) return true;
            if(accepts(slot, empty)) click(slot, 0);
        }
        return carried().isEmpty();
    }

    public boolean renderedByHost(){
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen && screen.getMenu() == menu;
    }

    private boolean accepts(Slot slot, boolean empty){
        if(slot.container != player.getInventory() || !slot.mayPlace(carried())) return false;
        var existing = slot.getItem();
        return empty ? existing.isEmpty() : !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, carried())
                && existing.getCount() < slot.getMaxStackSize(carried());
    }

    private boolean click(Slot slot, int button){
        var mc = Minecraft.getInstance();
        if(mc.gameMode == null || mc.player != player || player.containerMenu != menu) return false;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, button, ClickType.PICKUP, mc.player);
        return true;
    }
}
