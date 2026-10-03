package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.core.registries.Registries;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemSearchQueryTest{
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    private static final ItemSearchQuery.Entry IRON = new ItemSearchQuery.Entry("minecraft:iron_ingot", "Iron Ingot", "Minecraft", List.of("c:ingots/iron", "c:ingots"));
    private static final ItemSearchQuery.Entry SHEET = new ItemSearchQuery.Entry("create:iron_sheet", "Iron Sheet", "Create", List.of("c:plates/iron"));

    @Test void combinesNamesModsAndTags(){
        assertTrue(ItemSearchQuery.compile("@MINECRAFT #c:ingots iron").test(IRON));
        assertFalse(ItemSearchQuery.compile("@create #ingots").test(IRON));
        assertTrue(ItemSearchQuery.compile("@[create] #[plates/iron]").test(SHEET));
        assertTrue(ItemSearchQuery.compile("$ingots").test(IRON));
        assertFalse(ItemSearchQuery.compile("#iron").test(new ItemSearchQuery.Entry("test:iron", "Iron", "Test", List.of())));
    }

    @Test void supportsAlternativesExclusionsAndPhrases(){
        assertTrue(ItemSearchQuery.compile("@create -#ingots | \"iron ingot\"").test(IRON));
        assertTrue(ItemSearchQuery.compile("@create -#ingots | \"iron ingot\"").test(SHEET));
        assertFalse(ItemSearchQuery.compile("iron -sheet").test(SHEET));
        var mod = new ItemSearchQuery.Entry("ae2:controller", "ME Controller", "Applied Energistics 2", List.of());
        assertTrue(ItemSearchQuery.compile("@\"applied energistics\" controller").test(mod));
        assertTrue(ItemSearchQuery.compile("@ae2").test(mod));
    }

    @Test void treatsPunctuationAsTextAndAcceptsAnEmptyQuery(){
        assertTrue(ItemSearchQuery.compile("").test(IRON));
        assertTrue(ItemSearchQuery.compile("minecraft:iron_ingot").test(IRON));
        assertFalse(ItemSearchQuery.compile("[.*").test(IRON));
    }

    @Test void plainTextMatchesItemNamesAndPathsButNotModNamespaces(){
        var thruster = new ItemSearchQuery.Entry("thrustersandthings:copper_thruster", "Copper Thruster",
                "Thrusters and Things", List.of());
        var casing = new ItemSearchQuery.Entry("thrustersandthings:bronze_casing", "Bronze Casing",
                "Thrusters and Things", List.of());
        assertTrue(ItemSearchQuery.compile("thruster").test(thruster));
        assertFalse(ItemSearchQuery.compile("thruster").test(casing));
        assertTrue(ItemSearchQuery.compile("@thruster").test(thruster));
        assertTrue(ItemSearchQuery.compile("@thruster").test(casing));
        assertTrue(ItemSearchQuery.compile("thrustersandthings:bronze_casing").test(casing));
    }

    // Both item browsers must read the actual tag stream rather than filtering only registry text
    @Test void itemMetadataMatchesTagsAndModNamespace(){
        var stack = mock(ItemStack.class);
        when(stack.getHoverName()).thenReturn(Component.literal("Iron Plate"));
        when(stack.getTags()).thenReturn(Stream.of(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "plates/iron"))));
        var mods = mock(ModList.class);
        when(mods.getModContainerById("create")).thenReturn(Optional.empty());
        try(var active = mockStatic(ModList.class)){
            active.when(ModList::get).thenReturn(mods);
            var entry = ItemSearchQuery.item(ResourceLocation.fromNamespaceAndPath("create", "iron_sheet"), stack);
            assertTrue(ItemSearchQuery.compile("@create #c:plates/iron").test(entry));
            assertTrue(ItemSearchQuery.compile("iron -ingot").test(entry));
            assertFalse(ItemSearchQuery.compile("#c:ingots").test(entry));
        }
    }
}
