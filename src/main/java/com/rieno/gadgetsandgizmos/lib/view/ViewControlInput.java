package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.nbt.CompoundTag;

// Carry relative motion and optional absolute settings from one control surface
public record ViewControlInput(double pan, double tilt, double fov, String mode, int flashlight){
    // Reject invalid numbers and unknown settings before any source is changed
    public boolean valid(){
        if(mode == null || !Double.isFinite(pan) || !Double.isFinite(tilt) || !Double.isFinite(fov)
                || Math.abs(pan) > 3 || Math.abs(tilt) > 3 || flashlight < -1 || flashlight > 1) return false;
        if(fov != -1 && (fov < 5 || fov > 120)) return false;
        try{ if(!mode.isEmpty()) ViewRig.Mode.valueOf(mode); }
        catch(IllegalArgumentException err){ return false; }
        return true;
    }
    // Retain unspecified settings from the source's current state
    public ViewControlState settings(ViewControlState prev){
        return new ViewControlState(mode.isEmpty() ? prev.mode() : ViewRig.Mode.valueOf(mode),
                fov == -1 ? prev.fov() : fov, flashlight == -1 ? prev.flashlight() : flashlight == 1);
    }
    // Encode the same control command for tablet actions and display packets
    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.putDouble("Pan", pan);
        tag.putDouble("Tilt", tilt);
        tag.putDouble("Fov", fov);
        tag.putString("Mode", mode);
        tag.putInt("Flashlight", flashlight);
        return tag;
    }
    // Read optional settings without interpreting an absent field as a request
    public static ViewControlInput fromTag(CompoundTag tag){
        return new ViewControlInput(tag.getDouble("Pan"), tag.getDouble("Tilt"),
                tag.contains("Fov") ? tag.getDouble("Fov") : -1, tag.getString("Mode"),
                tag.contains("Flashlight") ? tag.getInt("Flashlight") : -1);
    }
}
