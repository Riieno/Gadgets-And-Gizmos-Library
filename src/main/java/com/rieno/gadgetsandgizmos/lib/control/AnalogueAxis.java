package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
import java.util.Map;

// Normalize one signed or unsigned analogue axis before it reaches a control channel
public class AnalogueAxis {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double EPSILON = 1.0E-4D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Analogue axis id
    private final String id;
    // Negative channel
    private final AnalogueChannel negativeChannel;
    // Positive channel
    private final AnalogueChannel positiveChannel;
    // Current deadzone
    private double deadzone;
    // Current smoothing
    private double smoothing;
    // Current filtered signed value
    private double filteredSignedValue;
    // Current signed value
    private double signedValue;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue axis
    public AnalogueAxis(String id, AnalogueChannel negativeChannel, AnalogueChannel positiveChannel) {
        this.id = id;
        this.negativeChannel = negativeChannel;
        this.positiveChannel = positiveChannel;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the id
    public String id() {
        return id;
    }

    // Get the negative channel
    public AnalogueChannel negativeChannel() {
        return negativeChannel;
    }

    // Get the positive channel
    public AnalogueChannel positiveChannel() {
        return positiveChannel;
    }

    // Get the deadzone
    public double getDeadzone() {
        return deadzone;
    }

    // Set the deadzone
    public void setDeadzone(double deadzone) {
        this.deadzone = Mth.clamp(deadzone, 0.0D, 0.95D);
    }

    // Get the smoothing
    public double getSmoothing() {
        return smoothing;
    }

    // Set the smoothing
    public void setSmoothing(double smoothing) {
        this.smoothing = Mth.clamp(smoothing, 0.0D, 0.98D);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the analogue state
    public boolean tick() {
        double prev = signedValue;
        double raw = Mth.clamp(positiveChannel.getUnsignedValue() - negativeChannel.getUnsignedValue(), -1.0D, 1.0D);
        if (Math.abs(raw) <= deadzone) {
            raw = 0.0D;
        }
        if (smoothing > 0.0D) {
            double alpha = Mth.clamp(1.0D - smoothing, 0.05D, 1.0D);
            filteredSignedValue += (raw - filteredSignedValue) * alpha;
        } else {
            filteredSignedValue = raw;
        }
        if (raw == 0.0D && Math.abs(filteredSignedValue) <= deadzone) {
            filteredSignedValue = 0.0D;
        }
        signedValue = filteredSignedValue;
        if (Math.abs(filteredSignedValue) <= deadzone) {
            signedValue = 0.0D;
        }
        return Math.abs(prev - signedValue) > EPSILON;
    }

    // Get the signed value
    public double getSignedValue() {
        return signedValue;
    }

    // Get the positive value
    public double getPositiveValue() {
        return Math.max(0.0D, signedValue);
    }

    // Get the negative value
    public double getNegativeValue() {
        return Math.max(0.0D, -signedValue);
    }

    // Get the positive redstone
    public int getPositiveRedstone() {
        return AnalogueChannel.toRedstone(getPositiveValue());
    }

    // Get the negative redstone
    public int getNegativeRedstone() {
        return AnalogueChannel.toRedstone(getNegativeValue());
    }

    // Describe the analogue axis
    public Map<String, Object> describe() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("value", signedValue);
        info.put("positive", getPositiveValue());
        info.put("negative", getNegativeValue());
        info.put("positiveRedstone", getPositiveRedstone());
        info.put("negativeRedstone", getNegativeRedstone());
        info.put("deadzone", deadzone);
        info.put("smoothing", smoothing);
        return info;
    }

    // Write the tag
    public CompoundTag writeToTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putDouble("Deadzone", deadzone);
        tag.putDouble("Smoothing", smoothing);
        tag.putDouble("FilteredSignedValue", filteredSignedValue);
        tag.putDouble("SignedValue", signedValue);
        return tag;
    }

    // Read the tag
    public void readFromTag(CompoundTag tag) {
        deadzone = tag.contains("Deadzone") ? Mth.clamp(tag.getDouble("Deadzone"), 0.0D, 0.95D) : 0.0D;
        smoothing = tag.contains("Smoothing") ? Mth.clamp(tag.getDouble("Smoothing"), 0.0D, 0.98D) : 0.0D;
        signedValue = Mth.clamp(tag.contains("SignedValue") ? tag.getDouble("SignedValue") : 0.0D, -1.0D, 1.0D);
        filteredSignedValue = Mth.clamp(tag.contains("FilteredSignedValue")
                ? tag.getDouble("FilteredSignedValue")
                : signedValue, -1.0D, 1.0D);
    }
}
