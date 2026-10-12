package com.rieno.gadgetsandgizmos.lib.control;

import java.util.List;

// Convert explicitly selected redstone strengths and scalar control values
public enum SignalValueType{
    REDSTONE("redstone"),
    SCALAR("scalar");

    // Keep saved option ids stable for consuming mods
    private final String id;

    // Assign the serialized value type
    SignalValueType(String id){
        this.id = id;
    }

    // Return the stable serialized option
    public String id(){
        return id;
    }

    // List the explicit value types in dropdown order
    public static List<String> optionIds(){
        return List.of(REDSTONE.id, SCALAR.id);
    }

    // Default missing or invalid choices to redstone without inspecting the numeric value
    public static SignalValueType fromId(String id){
        return SCALAR.id.equals(id) ? SCALAR : REDSTONE;
    }

    // Round and clamp to a redstone strength from zero to fifteen
    public int toRedstoneStrength(double val){
        if(Double.isNaN(val)) return 0;
        double strength = this == SCALAR ? val * 15.0D : val;
        return (int)Math.round(Math.max(0.0D, Math.min(15.0D, strength)));
    }

    // Normalize a quantized redstone strength for signal transport
    public double toNormalizedRedstone(double val){
        return toRedstoneStrength(val) / 15.0D;
    }

    // Preserve continuous scalar precision for analogue receivers
    public double toScalar(double val){
        if(this == REDSTONE) return toNormalizedRedstone(val);
        if(Double.isNaN(val)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, val));
    }
}
