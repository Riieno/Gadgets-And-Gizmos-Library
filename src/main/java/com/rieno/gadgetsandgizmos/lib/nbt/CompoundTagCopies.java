package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Set;
import java.util.Objects;

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

    // Replace an authoritative field group, including fields absent from its current snapshot
    public static CompoundTag copyReplacing(CompoundTag src, CompoundTag replacements, Set<String> keys){
        CompoundTag res = copyExcept(src, keys);
        if(replacements == null || keys == null) return res;
        for(String key : keys){
            Tag val = replacements.get(key);
            if(val != null) res.put(key, val.copy());
        }
        return res;
    }

    // Compare frame metadata without walking separately versioned payloads
    public static boolean equalsExcept(CompoundTag first, CompoundTag second, Set<String> omitted){
        for(String key : first.getAllKeys()){
            if(omitted.contains(key)) continue;
            if(!Objects.equals(first.get(key), second.get(key))) return false;
        }
        for(String key : second.getAllKeys()){
            if(!omitted.contains(key) && !first.contains(key)) return false;
        }
        return true;
    }
}
