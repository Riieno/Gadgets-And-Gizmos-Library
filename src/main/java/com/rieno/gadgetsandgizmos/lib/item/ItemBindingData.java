package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// Remove an item's binding without discarding unrelated data or player naming
public final class ItemBindingData{
    private ItemBindingData(){}

    public static CompoundTag read(ItemStack stack, String root){
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(root);
    }

    public static boolean clear(ItemStack stack, String root, String label){
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if(!data.contains(root)) return false;
        data.remove(root);
        if(data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        if(stack.has(DataComponents.CUSTOM_NAME) && label != null && !label.isBlank()){
            var name = stack.get(DataComponents.CUSTOM_NAME);
            String text = name.getString();
            String suffix = " [" + label.trim() + "]";
            if(text.endsWith(suffix)){
                String base = text.substring(0, text.length() - suffix.length());
                if(base.equals(stack.getItem().getName(stack).getString())) stack.remove(DataComponents.CUSTOM_NAME);
                else stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(base).withStyle(name.getStyle()));
            }
        }
        return true;
    }
}
