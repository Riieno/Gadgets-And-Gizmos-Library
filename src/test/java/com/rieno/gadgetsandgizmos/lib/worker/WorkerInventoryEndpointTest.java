package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerInventoryEndpointTest{
    private static final WorkerResourceKey PLANKS = new WorkerResourceKey(WorkerResourceType.ITEM,
            ResourceLocation.withDefaultNamespace("oak_planks"));

    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    // Dependency planning and extraction must not reuse cargo reserved by the active operation
    @Test void excludesReservedCargoAndKeepsComponents(){
        var inventory = new ItemStackHandler(3);
        ItemStack reserved = new ItemStack(Items.OAK_PLANKS, 4);
        reserved.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved"));
        inventory.setStackInSlot(0, reserved);
        inventory.setStackInSlot(1, new ItemStack(Items.OAK_PLANKS, 8));
        var endpoint = endpoint(inventory, false);
        assertEquals(8, endpoint.contents().get(PLANKS));
        assertEquals(8, endpoint.available(PLANKS));
        assertEquals(4, endpoint.extract(PLANKS, 4, true).amount());
        assertEquals(8, endpoint.available(PLANKS));
        var extracted = endpoint.extract(PLANKS, 4, false);
        assertEquals(4, extracted.amount());
        assertTrue(extracted.payload().contains("Stack"));
        assertEquals(4, inventory.getStackInSlot(0).getCount());
        assertEquals(Component.literal("Reserved"), inventory.getStackInSlot(0).get(DataComponents.CUSTOM_NAME));
        assertEquals(0, endpoint.insert(extracted, false));
    }

    // Stage multiple recipe batches physically and support simulation without creating stock
    @Test void stagesOnlyWithinRealInventoryCapacity(){
        var inventory = new ItemStackHandler(1);
        var endpoint = endpoint(inventory, true);
        var packet = new WorkerResourcePacket(PLANKS, 70, null);
        assertEquals(64, endpoint.insert(packet, true));
        assertEquals(0, endpoint.available(PLANKS));
        assertEquals(64, endpoint.insert(packet, false));
        assertEquals(64, endpoint.available(PLANKS));
        assertEquals(0, endpoint.space(PLANKS));
        assertEquals(64, endpoint.extract(PLANKS, 70, false).amount());
        assertTrue(inventory.getStackInSlot(0).isEmpty());
    }

    @Test void selectsOnlyMatchingContainerVariant(){
        WorkerResourceKey bucket = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.withDefaultNamespace("bucket"));
        var inventory = new ItemStackHandler(2);
        ItemStack filled = new ItemStack(Items.BUCKET);
        filled.set(DataComponents.CUSTOM_NAME, Component.literal("Already full"));
        inventory.setStackInSlot(0, filled);
        inventory.setStackInSlot(1, new ItemStack(Items.BUCKET));
        var endpoint = new WorkerInventoryEndpoint(UUID.randomUUID(), () -> BlockPos.ZERO, inventory,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), stack -> true, false);
        java.util.function.Predicate<ItemStack> empty = stack -> !stack.has(DataComponents.CUSTOM_NAME);
        assertEquals(1L, endpoint.availableMatchingItem(bucket, empty));
        assertEquals(1L, endpoint.extractMatchingItem(bucket, 1, empty, true).amount());
        assertEquals(1L, endpoint.extractMatchingItem(bucket, 1, empty, false).amount());
        assertFalse(inventory.getStackInSlot(0).isEmpty());
        assertTrue(inventory.getStackInSlot(1).isEmpty());
    }

    private static WorkerInventoryEndpoint endpoint(ItemStackHandler inventory, boolean insertion){
        return new WorkerInventoryEndpoint(UUID.randomUUID(), () -> BlockPos.ZERO, inventory,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                stack -> !stack.has(DataComponents.CUSTOM_NAME), insertion);
    }
}
