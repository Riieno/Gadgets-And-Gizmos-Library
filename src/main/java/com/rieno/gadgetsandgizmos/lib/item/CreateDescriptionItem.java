package com.rieno.gadgetsandgizmos.lib.item;

import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

// Present the owning item's translated description with Create's Shift prompt.
public class CreateDescriptionItem extends Item{
    public CreateDescriptionItem(Properties properties){
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag){
        super.appendHoverText(stack, ctx, tooltip, flag);
        ItemDescription description = ItemDescription.create(this, FontHelper.Palette.GRAY_AND_WHITE);
        if(description == null || description.getCurrentLines().isEmpty()) return;
        tooltip.add(CommonComponents.EMPTY);
        tooltip.addAll(description.getCurrentLines());
    }
}
