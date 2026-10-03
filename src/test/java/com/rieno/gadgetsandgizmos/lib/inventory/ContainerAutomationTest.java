package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ContainerAutomationTest{
    @BeforeAll static void bootstrap(){ InventoryTestBootstrap.bootstrap(); }

    @Test void ownershipCannotBeReclaimedByAnotherPlayer(){
        var config = new ContainerAutomation();
        UUID owner = UUID.randomUUID();
        assertTrue(config.claim(owner));
        assertFalse(config.claim(UUID.randomUUID()));
        assertEquals(owner, config.owner());
    }

    @Test void targetsAreBoundedAndKeepExactComponents(){
        var config = new ContainerAutomation();
        for(int idx = 0; idx < 16; idx++){
            var item = new ItemStack(Items.STONE);
            item.set(DataComponents.CUSTOM_NAME, Component.literal("Target " + idx));
            assertTrue(config.setTarget(item, 64, "craft"));
        }
        assertFalse(config.setTarget(new ItemStack(Items.DIRT), 64, "transfer"));
        var item = config.targets().getFirst().item();
        assertTrue(config.setTarget(item, 128, "craft"));
        assertEquals(16, config.targets().size());
        assertTrue(config.setTarget(item, 0, ""));
        assertEquals(15, config.targets().size());
        assertFalse(config.setTarget(item, -1, ""));
        assertFalse(config.setTarget(item, 1, "x".repeat(65)));
    }

    @Test void pendingDeliveriesReduceStockDeficits(){
        var target = new ContainerAutomation.StockTarget(new ItemStack(Items.STONE), 64, "craft");
        assertEquals(24, target.deficit(16, 24));
        assertEquals(0, target.deficit(80, 0));
        var copy = target.item();
        copy.setCount(60);
        assertEquals(1, target.item().getCount());
    }

    @Test void ownerFilterControllerAndStockSurviveSerialization(){
        var provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var config = new ContainerAutomation();
        UUID owner = UUID.randomUUID();
        UUID body = UUID.randomUUID();
        config.claim(owner);
        config.setLocked(true); config.setPush(true); config.setPull(true);
        config.setFilter(new ItemStack(Items.STONE));
        config.setController(body, new BlockPos(1, 2, 3));
        config.setTarget(new ItemStack(Items.DIRT), 128, "craft");
        var res = ContainerAutomation.fromTag(config.toTag(provider), provider);
        assertEquals(owner, res.owner());
        assertTrue(res.locked()); assertTrue(res.push()); assertTrue(res.pull());
        assertTrue(res.filter().is(Items.STONE));
        assertEquals(body, res.controllerSubLevelId());
        assertEquals(new BlockPos(1, 2, 3), res.controllerPos());
        assertEquals(128, res.targets().getFirst().amount());
        assertEquals("craft", res.targets().getFirst().task());
    }
}
