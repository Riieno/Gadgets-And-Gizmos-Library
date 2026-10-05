package com.rieno.gadgetsandgizmos.lib.tooltip;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

// Build reusable tooltip components from block identity and boolean state
public final class TooltipComponents {
    // Prevent instantiation of the tooltip component helper
    private TooltipComponents() {
    }

    // Get the translated title of the actual block
    public static Component blockTitle(BlockState state) {
        return state.getBlock().getName().copy().withStyle(ChatFormatting.GOLD);
    }

    // Get a green or red status value
    public static Component status(boolean active, Component activeText, Component inactiveText) {
        return (active ? activeText : inactiveText).copy()
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
