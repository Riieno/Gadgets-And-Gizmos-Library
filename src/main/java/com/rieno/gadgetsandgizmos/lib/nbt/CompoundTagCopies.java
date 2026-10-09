package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

// Copy selected compound fields without visiting omitted payloads
public final class CompoundTagCopies{
    private CompoundTagCopies(){}

    // Retain independent nested values for every field that is included
    public static CompoundTag copyExcept(CompoundTag src, Set<String> omitted){
        CompoundTag res = new CompoundTag();
        if(src == null) return res;
        for(String key : src.getAllKeys()){
            if(omitted != null && omitted.contains(key)) continue;
            Tag val = src.get(key);
            if(val != null) res.put(key, val.copy());
        }
        return res;
    }
}
