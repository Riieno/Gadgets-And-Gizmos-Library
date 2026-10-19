package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

// Remove unavailable item references before inventory and configuration codecs read them
public final class ItemStackNbtSanitizer{
    private ItemStackNbtSanitizer(){}

    // Copy NBT while preserving installed items and dropping unavailable item components
    public static CompoundTag withoutUnavailableItems(CompoundTag data){
        CompoundTag res = data.copy();
        return pruneItems(res) ? res : new CompoundTag();
    }

    // Keep valid configuration while dropping missing stacks from nested inventories and filters
    private static boolean pruneItems(Tag data){
        if(data instanceof CompoundTag tag){
            if(tag.contains("id", Tag.TAG_STRING) && (tag.contains("count", Tag.TAG_ANY_NUMERIC) || tag.contains("Count", Tag.TAG_ANY_NUMERIC))){
                var id = ResourceLocation.tryParse(tag.getString("id"));
                if(id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
                CompoundTag components = tag.getCompound("components");
                for(String key : Set.copyOf(components.getAllKeys())){
                    var type = ResourceLocation.tryParse(key.startsWith("!") ? key.substring(1) : key);
                    if(type == null || !BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(type)) components.remove(key);
                }
            }
            for(String key : Set.copyOf(tag.getAllKeys())) if(!pruneItems(tag.get(key))) tag.remove(key);
        }else if(data instanceof ListTag list){
            for(int idx = list.size() - 1; idx >= 0; idx--) if(!pruneItems(list.get(idx))) list.remove(idx);
        }
        return true;
    }
}
