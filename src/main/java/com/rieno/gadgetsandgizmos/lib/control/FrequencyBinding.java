package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

// Store and serialize one Create Redstone Link frequency pair
public class FrequencyBinding {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Channel id
    private final String channelId;
    // Current first entry
    private ItemStack first = ItemStack.EMPTY;
    // Current second entry
    private ItemStack second = ItemStack.EMPTY;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the frequency binding
    public FrequencyBinding(String channelId) {
        this.channelId = channelId == null ? "" : channelId;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the channel id
    public String channelId() {
        return channelId;
    }

    // Get the first
    public ItemStack first() {
        return first.copy();
    }

    // Get the second
    public ItemStack second() {
        return second.copy();
    }

    // Check if this is bound
    public boolean isBound() {
        return !first.isEmpty() || !second.isEmpty();
    }

    // Set the frequency binding
    public void set(ItemStack first, ItemStack second) {
        this.first = normalize(first);
        this.second = normalize(second);
    }

    // Clear the frequency binding
    public void clear() {
        first = ItemStack.EMPTY;
        second = ItemStack.EMPTY;
    }

    // Write the frequency binding data
    public CompoundTag toTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("ChannelId", channelId);
        if (!first.isEmpty()) {
            tag.put("First", first.saveOptional(provider));
        }
        if (!second.isEmpty()) {
            tag.put("Second", second.saveOptional(provider));
        }
        return tag;
    }

    // Read the frequency binding
    public void read(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("First")) {
            first = normalize(ItemStack.parseOptional(provider, tag.getCompound("First")));
        } else {
            first = ItemStack.EMPTY;
        }
        if (tag.contains("Second")) {
            second = normalize(ItemStack.parseOptional(provider, tag.getCompound("Second")));
        } else {
            second = ItemStack.EMPTY;
        }
    }

    // Read the frequency binding data
    public static FrequencyBinding fromTag(CompoundTag tag, HolderLookup.Provider provider) {
        FrequencyBinding binding = new FrequencyBinding(tag.getString("ChannelId"));
        binding.read(tag, provider);
        return binding;
    }

    // Normalize the frequency binding
    private static ItemStack normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
