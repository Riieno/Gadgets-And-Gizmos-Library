package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.nbt.CompoundTag;

// Describe settings shared by projected and handheld view controls
public record ViewControlState(ViewRig.Mode mode, double fov, boolean flashlight, boolean autoTracking, boolean lockTarget){
    // Keep existing callers opted out of target tracking
    public ViewControlState(ViewRig.Mode mode, double fov, boolean flashlight){ this(mode, fov, flashlight, false); }
    // Leave dynamic acquisition enabled for callers that do not specify target locking
    public ViewControlState(ViewRig.Mode mode, double fov, boolean flashlight, boolean autoTracking){
        this(mode, fov, flashlight, autoTracking, false);
    }
    // Save the controls without depending on a consuming block implementation
    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", mode.name());
        tag.putDouble("Fov", fov);
        tag.putBoolean("Flashlight", flashlight);
        tag.putBoolean("AutoTracking", autoTracking);
        tag.putBoolean("LockTarget", lockTarget);
        return tag;
    }
    // Read synchronized settings with safe defaults for older display frames
    public static ViewControlState fromTag(CompoundTag tag){
        ViewRig.Mode mode;
        try{ mode = ViewRig.Mode.valueOf(tag.getString("Mode")); }
        catch(IllegalArgumentException err){ mode = ViewRig.Mode.LOCKED; }
        double fov = tag.contains("Fov") ? tag.getDouble("Fov") : 70;
        return new ViewControlState(mode, Double.isFinite(fov) ? Math.clamp(fov, 5, 120) : 70,
                tag.getBoolean("Flashlight"), tag.getBoolean("AutoTracking"), tag.getBoolean("LockTarget"));
    }
}
