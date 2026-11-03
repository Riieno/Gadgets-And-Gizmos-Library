package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Encode UUID-keyed labels and tolerate malformed identifiers when reading stored metadata
public final class UuidStringTags{
    private UuidStringTags(){}
    public static CompoundTag write(Map<UUID, String> entries){
        CompoundTag res = new CompoundTag();
        entries.forEach((id, val) -> res.putString(id.toString(), val));
        return res;
    }
    public static Map<UUID, String> read(CompoundTag data){
        Map<UUID, String> res = new LinkedHashMap<>();
        for(String key : data.getAllKeys()){
            try{ res.put(UUID.fromString(key), data.getString(key)); }
            catch(IllegalArgumentException ignored){}
        }
        return res;
    }
}
