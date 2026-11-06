package com.rieno.gadgetsandgizmos.lib.item;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem;
import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

// Retain Create's cogwheel placement helpers while displaying the owning item's description
public class DescribedCogwheelBlockItem extends CogwheelBlockItem{
    // Register the normal small or large cogwheel placement helpers
    public DescribedCogwheelBlockItem(CogWheelBlock block, Item.Properties props){
        super(block, props);
    }

    // Append the translated Create description using its normal modifier-key presentation
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag){
        super.appendHoverText(stack, ctx, tooltip, flag);
        ItemDescription description = ItemDescription.create(this, FontHelper.Palette.GRAY_AND_WHITE);
        if(description == null || description.getCurrentLines().isEmpty()) return;
        tooltip.add(CommonComponents.EMPTY);
        tooltip.addAll(description.getCurrentLines());
    }
}
