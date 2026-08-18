package com.rieno.gadgetsandgizmos.lib.item;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

// Carry a packaged texture on an ordinary vanilla supporter head stack
public final class BundledSupporterHeadStack {

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           Constants
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/
    // Namespaced custom-data root
    private static final String ROOT_TAG = "gadgetsngizmos:bundled_player_head";
    // Packaged texture resource ID
    private static final String TEXTURE_TAG = "Texture";

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        PRELOAD / SETUP
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bundled supporter head stack helper
    private BundledSupporterHeadStack() {
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           Functions
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Create a vanilla player head backed by a packaged supporter texture
    public static ItemStack create(ResourceLocation texture, Component displayName) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        setTexture(stack, texture);
        stack.set(DataComponents.ITEM_NAME,
                Objects.requireNonNull(displayName, "Supporter head display name").copy());
        return stack;
    }

    // Store the packaged texture on a vanilla player head
    public static void setTexture(ItemStack stack, ResourceLocation texture) {
        Objects.requireNonNull(stack, "Bundled supporter head stack");
        if (!stack.is(Items.PLAYER_HEAD)) {
            throw new IllegalArgumentException("Bundled supporter head stack must use minecraft:player_head");
        }
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag supporterHeadTag = new CompoundTag();
        supporterHeadTag.putString(TEXTURE_TAG,
                Objects.requireNonNull(texture, "Supporter head texture").toString());
        customTag.put(ROOT_TAG, supporterHeadTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
    }

    // Get the packaged texture from a vanilla player head
    public static @Nullable ResourceLocation texture(@Nullable ItemStack stack) {
        if (stack == null || !stack.is(Items.PLAYER_HEAD)) {
            return null;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag customTag = customData.copyTag();
        if (!customTag.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        return ResourceLocation.tryParse(customTag.getCompound(ROOT_TAG).getString(TEXTURE_TAG));
    }
}
