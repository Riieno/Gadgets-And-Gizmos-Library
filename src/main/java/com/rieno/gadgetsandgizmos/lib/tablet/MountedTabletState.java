package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.nbt.CompoundTag;

// Persist the selected app tab on a mounted display, independently of its viewers
public final class MountedTabletState{
    private String tab = "contents";

    public String tab(){ return tab; }

    public boolean select(String value){
        if(value == null || value.isBlank() || value.length() > 64 || value.equals(tab)) return false;
        tab = value;
        return true;
    }

    public void read(CompoundTag tag){
        tab = tag.contains("MountedTabletTab") ? tag.getString("MountedTabletTab") : "contents";
    }

    public void write(CompoundTag tag){ tag.putString("MountedTabletTab", tab); }

    public interface Host{
        MountedTabletState mountedTabletState();
    }
}
