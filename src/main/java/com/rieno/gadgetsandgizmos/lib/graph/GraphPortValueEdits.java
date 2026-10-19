package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;

// Distinguish authored port edits from older live values while a host merges a graph draft
public final class GraphPortValueEdits{
    public static final String TAG = "AuthoredPortEdits";
    private GraphPortValueEdits(){}

    public static void mark(CompoundTag data, String key){
        CompoundTag edits = data.getCompound(TAG);
        edits.putBoolean(key, true);
        data.put(TAG, edits);
    }

    public static boolean contains(CompoundTag data, String key){ return data.getCompound(TAG).getBoolean(key); }
    public static void clear(CompoundTag data){ data.remove(TAG); }
}
