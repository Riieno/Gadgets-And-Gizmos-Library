package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ItemFoilRenderScopeTest {
    @BeforeAll static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.neoforged.fml.ModList.of(java.util.List.of(), java.util.List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void nestedItemRendersKeepEachItemsOverride(){
        ItemFoilRenderScope.withoutFoil(Items.STICK, () -> {
            assertEquals(false, ItemFoilRenderScope.overrideFor(Items.STICK));
            assertNull(ItemFoilRenderScope.overrideFor(Items.DIAMOND));
            ItemFoilRenderScope.withoutFoil(Items.DIAMOND, () -> {
                assertEquals(false, ItemFoilRenderScope.overrideFor(Items.STICK));
                assertEquals(false, ItemFoilRenderScope.overrideFor(Items.DIAMOND));
            });
            assertEquals(false, ItemFoilRenderScope.overrideFor(Items.STICK));
            assertNull(ItemFoilRenderScope.overrideFor(Items.DIAMOND));
        });
        assertNull(ItemFoilRenderScope.overrideFor(Items.STICK));
    }

    @Test void rendererFailureRestoresThePreviousScope(){
        ItemFoilRenderScope.withoutFoil(Items.STICK, () -> {
            assertThrows(IllegalStateException.class, () -> ItemFoilRenderScope.withoutFoil(Items.STICK, () -> {
                throw new IllegalStateException("render failed");
            }));
            assertEquals(false, ItemFoilRenderScope.overrideFor(Items.STICK));
        });
        assertNull(ItemFoilRenderScope.overrideFor(Items.STICK));
    }

    @Test void overridesDoNotReachAnotherThread(){
        ItemFoilRenderScope.withoutFoil(Items.STICK, () ->
                assertNull(CompletableFuture.supplyAsync(() -> ItemFoilRenderScope.overrideFor(Items.STICK)).join()));
    }
}
