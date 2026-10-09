package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.nbt.CompoundTag;

// Describe a live controller owner for synchronized provider displays
public record ControllerOwnership(String channel, String displayKey){
    public static final ControllerOwnership NONE = new ControllerOwnership("", "");

    public ControllerOwnership{
        channel = channel == null ? "" : channel;
        displayKey = displayKey == null ? "" : displayKey;
        if(channel.isBlank() || displayKey.isBlank()){ channel = ""; displayKey = ""; }
    }

    public boolean present(){ return !channel.isEmpty(); }

    // Keep another channel from relabeling an owned provider
    public ControllerOwnership claim(String channel, String displayKey){
        if(present() && !this.channel.equals(channel)) return this;
        return new ControllerOwnership(channel, displayKey);
    }

    public ControllerOwnership release(String channel){ return this.channel.equals(channel) ? NONE : this; }

    public CompoundTag save(){
        CompoundTag tag = new CompoundTag();
        if(present()){ tag.putString("Channel", channel); tag.putString("DisplayKey", displayKey); }
        return tag;
    }

    public static ControllerOwnership read(CompoundTag tag){
        return tag == null ? NONE : new ControllerOwnership(tag.getString("Channel"), tag.getString("DisplayKey"));
    }
}
