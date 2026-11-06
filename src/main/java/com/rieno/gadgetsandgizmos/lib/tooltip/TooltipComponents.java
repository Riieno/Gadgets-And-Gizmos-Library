package com.rieno.gadgetsandgizmos.lib.tooltip;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

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

    // Add a shared tooltip line once while preserving existing descriptions and formatting
    public static void appendOnce(List<Component> lines, Component line){
        if(!lines.contains(line)) lines.add(line);
    }

    // Append the owning item's translated Create description and modifier-key hints
    public static void appendCreateDescription(Item item, List<Component> lines){
        ItemDescription description = ItemDescription.create(item, FontHelper.Palette.GRAY_AND_WHITE);
        if(description == null || description.getCurrentLines().isEmpty()) return;
        lines.add(CommonComponents.EMPTY);
        lines.addAll(description.getCurrentLines());
    }
}
