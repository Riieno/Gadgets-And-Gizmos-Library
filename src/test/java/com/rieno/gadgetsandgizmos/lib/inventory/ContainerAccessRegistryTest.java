package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContainerAccessRegistryTest{
    @BeforeAll static void bootstrap(){ InventoryTestBootstrap.bootstrap(); }

    @Test void cachedHandlersRecheckCurrentPermissions(){
        var level = mock(Level.class);
        var open = new AtomicBoolean(true);
        ContainerAccessRegistry.register(new ContainerAccessRegistry.Provider(){
            @Override public boolean canOpen(ServerPlayer player, Level target, BlockPos pos){ return target != level || open.get(); }
            @Override public boolean canInsert(Level target, BlockPos pos, ItemStack stack){ return target != level || open.get(); }
            @Override public boolean canExtract(Level target, BlockPos pos, ItemStack stack){ return target != level || open.get(); }
        });
        var inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(Items.STONE, 12));
        var handler = ContainerAccessRegistry.wrap(level, BlockPos.ZERO, inventory);
        assertEquals(3, handler.extractItem(0, 3, true).getCount());
        open.set(false);
        assertTrue(handler.extractItem(0, 3, false).isEmpty());
        assertEquals(4, handler.insertItem(0, new ItemStack(Items.STONE, 4), false).getCount());
        assertEquals(12, inventory.getStackInSlot(0).getCount());
        assertFalse(ContainerAccessRegistry.canOpen(mock(ServerPlayer.class), handler));
        open.set(true);
        assertEquals(3, handler.extractItem(0, 3, false).getCount());
    }

    @Test void differentSidesOfSameStorageCannotSelfTransfer(){
        var level = mock(Level.class);
        var inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(Items.STONE, 12));
        var source = ContainerAccessRegistry.wrap(level, BlockPos.ZERO, inventory);
        var target = ContainerAccessRegistry.wrap(level, BlockPos.ZERO, inventory);
        assertTrue(ContainerAccessRegistry.sameStorage(source, target));
        assertEquals(0, ItemTransfers.move(source, target, stack -> true, 12));
        assertEquals(12, inventory.getStackInSlot(0).getCount());
    }
}
