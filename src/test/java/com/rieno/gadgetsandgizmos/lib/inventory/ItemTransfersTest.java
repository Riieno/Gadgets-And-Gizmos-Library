package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ItemTransfersTest{
    @BeforeAll static void bootstrap(){ InventoryTestBootstrap.bootstrap(); }

    @Test void transferRespectsBudgetFilterAndDestinationCapacity(){
        var source = new ItemStackHandler(2);
        var target = new ItemStackHandler(1);
        source.setStackInSlot(0, new ItemStack(Items.DIRT, 32));
        source.setStackInSlot(1, new ItemStack(Items.STONE, 32));
        target.setStackInSlot(0, new ItemStack(Items.STONE, 60));
        assertEquals(4, ItemTransfers.move(source, target, stack -> stack.is(Items.STONE), 8));
        assertEquals(32, source.getStackInSlot(0).getCount());
        assertEquals(28, source.getStackInSlot(1).getCount());
        assertEquals(64, target.getStackInSlot(0).getCount());
    }

    @Test void rejectedRealInsertionReturnsItemsToSource(){
        var source = new ItemStackHandler(1);
        source.setStackInSlot(0, new ItemStack(Items.STONE, 20));
        var target = new ItemStackHandler(1){
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate){ return simulate ? ItemStack.EMPTY : stack; }
        };
        assertEquals(0, ItemTransfers.move(source, target, stack -> true, 16));
        assertEquals(20, source.getStackInSlot(0).getCount());
    }

    @Test void outputOnlySourceUsesRecoverySinkWithoutLosingItems(){
        var source = new ItemStackHandler(1){
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate){ return stack; }
        };
        source.setStackInSlot(0, new ItemStack(Items.STONE, 20));
        var target = new ItemStackHandler(1){
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate){ return simulate ? ItemStack.EMPTY : stack; }
        };
        var recovered = new ArrayList<ItemStack>();
        assertEquals(0, ItemTransfers.move(source, target, stack -> true, 16, recovered::add));
        assertEquals(20, source.getStackInSlot(0).getCount() + recovered.stream().mapToInt(ItemStack::getCount).sum());
    }

    @Test void sameStorageIsNeverTransferred(){
        var storage = new ItemStackHandler(1);
        storage.setStackInSlot(0, new ItemStack(Items.STONE, 20));
        assertEquals(0, ItemTransfers.move(storage, storage, stack -> true, 16));
        assertEquals(20, storage.getStackInSlot(0).getCount());
    }

    @Test void countingDistinguishesItemComponents(){
        var storage = new ItemStackHandler(2);
        var named = new ItemStack(Items.STONE, 9);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved"));
        storage.setStackInSlot(0, new ItemStack(Items.STONE, 12));
        storage.setStackInSlot(1, named);
        assertEquals(12, ItemTransfers.count(storage, new ItemStack(Items.STONE)));
        assertEquals(9, ItemTransfers.count(storage, named));
    }
}
