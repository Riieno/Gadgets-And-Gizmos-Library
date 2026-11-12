package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

// Select an authored input before a legacy fallback without testing its truth or magnitude
public final class GraphInputDefaults{
    private GraphInputDefaults(){}

    // Preserve authored whitespace and empty strings before consulting legacy properties
    public static String text(CompoundTag data, String port, String fallback){
        CompoundTag val = configuredValue(data, port);
        return val.getCompound("Payload").contains("Value", Tag.TAG_STRING)
                ? val.getCompound("Payload").getString("Value") : fallback;
    }

    // Read an authored literal or legacy property without substituting runtime values
    public static CompoundTag configuredValue(CompoundTag data, String port){
        if(port == null || port.isEmpty()) return new CompoundTag();
        CompoundTag defaults = data.getCompound("Defaults");
        if(defaults.contains(port, Tag.TAG_COMPOUND)) return defaults.getCompound(port).copy();
        String property = Character.toUpperCase(port.charAt(0)) + port.substring(1);
        CompoundTag encoded = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        if(data.contains(property, Tag.TAG_DOUBLE) || data.contains(property, Tag.TAG_INT)
                || data.contains(property, Tag.TAG_FLOAT)){
            encoded.putString("Type", "number");
            payload.putDouble("Value", data.getDouble(property));
        }else if(data.contains(property, Tag.TAG_BYTE)){
            encoded.putString("Type", "boolean");
            payload.putBoolean("Value", data.getBoolean(property));
        }else if(data.contains(property, Tag.TAG_STRING)){
            encoded.putString("Type", "string");
            payload.putString("Value", data.getString(property));
        }else return encoded;
        encoded.put("Payload", payload);
        return encoded;
    }

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
