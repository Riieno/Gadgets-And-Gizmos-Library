package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.nbt.CompoundTag;

// Describe settings shared by projected and handheld view controls
public record ViewControlState(ViewRig.Mode mode, double fov, boolean flashlight){
    // Save the controls without depending on a consuming block implementation
    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", mode.name());
        tag.putDouble("Fov", fov);
        tag.putBoolean("Flashlight", flashlight);
        return tag;
    }
    // Read synchronized settings with safe defaults for older display frames
    public static ViewControlState fromTag(CompoundTag tag){
        ViewRig.Mode mode;
        try{ mode = ViewRig.Mode.valueOf(tag.getString("Mode")); }
        catch(IllegalArgumentException err){ mode = ViewRig.Mode.LOCKED; }
        double fov = tag.contains("Fov") ? tag.getDouble("Fov") : 70;
        return new ViewControlState(mode, Double.isFinite(fov) ? Math.clamp(fov, 5, 120) : 70,
                tag.getBoolean("Flashlight"));
    }
}
