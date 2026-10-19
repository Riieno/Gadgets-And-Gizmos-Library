package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

// Select an authored input before a legacy fallback without testing its truth or magnitude
public final class GraphInputDefaults{
    private GraphInputDefaults(){}

    // Connections and explicit literals own the preferred port, including false, zero and empty text
    public static String selectPort(CompoundTag data, String preferred, String fallback, boolean connected){
        if(connected || data.getCompound("Defaults").contains(preferred, Tag.TAG_COMPOUND)) return preferred;
        if(preferred.isEmpty()) return fallback;
        String property = Character.toUpperCase(preferred.charAt(0)) + preferred.substring(1);
        return data.contains(property, Tag.TAG_DOUBLE) || data.contains(property, Tag.TAG_INT)
                || data.contains(property, Tag.TAG_FLOAT) || data.contains(property, Tag.TAG_BYTE)
                || data.contains(property, Tag.TAG_STRING) ? preferred : fallback;
    }
}
