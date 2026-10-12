package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.function.IntConsumer;
import java.util.function.Predicate;

// Consume wheel input over a player's own equipped armor slot
public final class EquipmentSlotScrollHandler{
    private EquipmentSlotScrollHandler(){}

    // Match the inventory backing slot, including creative inventory wrappers
    public static boolean matchesSlot(Slot slot, Player player, EquipmentSlot equipment){
        return slot != null && player != null && equipment != null
                && equipment.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && slot.isActive() && slot.container == player.getInventory()
                && slot.getSlotIndex() == Inventory.INVENTORY_SIZE + equipment.getIndex();
    }

    // Send one signed step while leaving other slots and carried items alone
    public static boolean handle(ScreenEvent.MouseScrolled.Pre evt, Player player, EquipmentSlot equipment,
                                 Predicate<ItemStack> accepts, IntConsumer onScroll){
        double delta = evt.getScrollDeltaY();
        if(!Double.isFinite(delta) || delta == 0 || !(evt.getScreen() instanceof AbstractContainerScreen<?> screen)) return false;
        Slot slot = screen.getSlotUnderMouse();
        if(!matchesSlot(slot, player, equipment) || !screen.getMenu().getCarried().isEmpty()) return false;
        if(!accepts.test(slot.getItem())) return false;
        onScroll.accept(delta > 0 ? 1 : -1);
        evt.setCanceled(true);
        return true;
    }
}
