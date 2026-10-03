package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemInventoryCapacityTest{
    @BeforeAll static void bootstrap(){ InventoryTestBootstrap.bootstrap(); }

    // Recover slots occupied by repeated deliveries while preserving distinct stack components
    @Test void compactsOnlyMatchingUnreservedStacks(){
        var inventory = new ItemStackHandler(5);
        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 40));
        inventory.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 20));
        inventory.setStackInSlot(2, new ItemStack(Items.IRON_INGOT, 10));
        var named = new ItemStack(Items.IRON_INGOT, 2);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved"));
        inventory.setStackInSlot(3, named);
        ItemInventoryCapacity.compact(inventory, stack -> !stack.has(DataComponents.CUSTOM_NAME));
        assertEquals(64, inventory.getStackInSlot(0).getCount());
        assertEquals(6, inventory.getStackInSlot(1).getCount());
        assertTrue(inventory.getStackInSlot(2).isEmpty());
        assertEquals(2, inventory.getStackInSlot(3).getCount());
    }

    // Count the complete inventory while bounding a smaller request and leaving every slot unchanged
    @Test void countsAllTwentySevenSlots(){
        var inventory = new ItemStackHandler(27);
        var template = new ItemStack(Items.IRON_INGOT);
        assertEquals(1728, ItemInventoryCapacity.insertable(inventory, template, Long.MAX_VALUE));
        assertEquals(1024, ItemInventoryCapacity.insertable(inventory, template, 1024));
        for(int idx = 0; idx < inventory.getSlots(); idx++) assertTrue(inventory.getStackInSlot(idx).isEmpty());
    }

    // Partial stacks accept only compatible components and leave unrelated items untouched
    @Test void respectsComponentsAndOccupiedSlots(){
        var inventory = new ItemStackHandler(3);
        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 48));
        var named = new ItemStack(Items.IRON_INGOT, 32);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Personal ingots"));
        inventory.setStackInSlot(1, named);
        assertEquals(80, ItemInventoryCapacity.insertable(inventory, new ItemStack(Items.IRON_INGOT), Long.MAX_VALUE));
        assertEquals(96, ItemInventoryCapacity.insertable(inventory, named, Long.MAX_VALUE));
        assertEquals(48, inventory.getStackInSlot(0).getCount());
        assertEquals(32, inventory.getStackInSlot(1).getCount());
    }

    // Honor restricted slots, sixteen-item stacks and unstackable items
    @Test void respectsItemAndSlotLimits(){
        var inventory = new ItemStackHandler(4){
            @Override public int getSlotLimit(int idx){ return idx == 0 ? 8 : 64; }
            @Override public boolean isItemValid(int idx, ItemStack stack){ return idx != 1; }
        };
        assertEquals(40, ItemInventoryCapacity.insertable(inventory, new ItemStack(Items.ENDER_PEARL), Long.MAX_VALUE));
        assertEquals(3, ItemInventoryCapacity.insertable(inventory, new ItemStack(Items.IRON_SWORD), Long.MAX_VALUE));
        assertEquals(0, ItemInventoryCapacity.insertable(inventory, new ItemStack(Items.IRON_INGOT), 0));
    }
}
