package com.rieno.gadgetsandgizmos.lib.display;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

// Resolve a user's selected source before applying automatic presentation preferences
public final class DisplaySourceSelection{
    private DisplaySourceSelection(){}

    // Keep presentation-only sources selectable beside ordinary payloads
    public static ListTag available(ListTag sources, ListTag presentations){
        ListTag res = new ListTag();
        Set<String> ids = new HashSet<>();
        for(ListTag list : new ListTag[]{sources, presentations}){
            if(list == null) continue;
            for(int idx = 0; idx < list.size(); idx++){
                CompoundTag src = list.getCompound(idx);
                String id = src.getString("Id");
                if(!id.isBlank() && ids.add(id)) res.add(src);
            }
        }
        return res;
    }

    // Retain the selected source while it remains available in the current source list
    public static @Nullable CompoundTag selected(ListTag sources, String selectedId){
        if(sources == null || selectedId == null || selectedId.isBlank()) return null;
        for(int idx = 0; idx < sources.size(); idx++){
            CompoundTag src = sources.getCompound(idx);
            if(selectedId.equals(src.getString("Id"))) return src;
        }
        return null;
    }
}
