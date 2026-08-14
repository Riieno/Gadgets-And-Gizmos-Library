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

// Keep one analogue input stable while deadzones, inversion and response curves are applied
public class AnalogueChannel {
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

    // Analogue channel id
    private final String id;
    // Tracks whether analogue channel is signed
    private final boolean signed;

    // Current analogue channel mode
    private AnalogueChannelMode mode = AnalogueChannelMode.RAMP;
    // Minimum value
    private double minValue;
    // Maximum value
    private double maxValue;
    // Rise rate
    private double riseRate = 0.08D;
    // Fall rate
    private double fallRate = 0.08D;
    // Current step amount
    private double stepAmount = 0.1D;
    // Current deadzone
    private double deadzone;
    // Current smoothing
    private double smoothing;
    // Debounce tick count
    private int debounceTicks;
    // Controls whether to reset to zero
    private boolean resetToZero = true;
    // Controls whether to repeat while held
    private boolean repeatWhileHeld;
    // Repeat interval tick count
    private int repeatIntervalTicks = 4;
    // Tracks whether analogue channel is pressed
    private boolean pressed;
    // Tracks whether analogue channel is latched
    private boolean latched;
    // Current direct value
    private double directValue;
    // Target value
    private double targetValue;
    // Current response value
    private double responseValue;
    // Current filtered value
    private double filteredValue;
    // Current analogue channel value
    private double value;
    // Last edge tick
    private long lastEdgeTick = Long.MIN_VALUE;
    // Last step tick
    private long lastStepTick = Long.MIN_VALUE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue channel
    public AnalogueChannel(String id, boolean signed) {
        this.id = id;
        this.signed = signed;
        this.minValue = signed ? -1.0D : 0.0D;
        this.maxValue = 1.0D;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the unsigned
    public static AnalogueChannel unsigned(String id) {
        return new AnalogueChannel(id, false);
    }

    // Get the signed
    public static AnalogueChannel signed(String id) {
        return new AnalogueChannel(id, true);
    }

    // Get the id
    public String id() {
        return id;
    }

    // Check if this is signed
    public boolean isSigned() {
        return signed;
    }

    // Get the mode
    public AnalogueChannelMode getMode() {
        return mode;
    }

    // Set the mode
    public void setMode(AnalogueChannelMode mode) {
        this.mode = mode == null ? AnalogueChannelMode.RAMP : mode;
        if (this.mode == AnalogueChannelMode.DIRECT) {
            targetValue = clampValue(directValue);
        }
    }

    // Get the min value
    public double getMinValue() {
        return minValue;
    }

    // Set the min value
    public void setMinValue(double minValue) {
        this.minValue = signed ? Mth.clamp(minValue, -1.0D, 1.0D) : Mth.clamp(minValue, 0.0D, 1.0D);
        if (this.minValue > maxValue) {
            this.minValue = maxValue;
        }
        targetValue = clampValue(targetValue);
        responseValue = clampValue(responseValue);
        filteredValue = clampValue(filteredValue);
        value = clampValue(value);
    }

    // Get the max value
    public double getMaxValue() {
        return maxValue;
    }

    // Set the max value
    public void setMaxValue(double maxValue) {
        this.maxValue = Mth.clamp(maxValue, signed ? -1.0D : 0.0D, 1.0D);
        if (this.maxValue < minValue) {
            this.maxValue = minValue;
        }
        targetValue = clampValue(targetValue);
        responseValue = clampValue(responseValue);
        filteredValue = clampValue(filteredValue);
        value = clampValue(value);
    }

    // Get the rise rate
    public double getRiseRate() {
        return riseRate;
    }

    // Set the rise rate
    public void setRiseRate(double riseRate) {
        this.riseRate = Math.max(0.0D, riseRate);
    }

    // Get the fall rate
    public double getFallRate() {
        return fallRate;
    }

    // Set the fall rate
    public void setFallRate(double fallRate) {
        this.fallRate = Math.max(0.0D, fallRate);
    }

    // Get the step amount
    public double getStepAmount() {
        return stepAmount;
    }

    // Set the step amount
    public void setStepAmount(double stepAmount) {
        this.stepAmount = Mth.clamp(stepAmount, -1.0D, 1.0D);
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

    // Get the debounce ticks
    public int getDebounceTicks() {
        return debounceTicks;
    }

    // Set the debounce ticks
    public void setDebounceTicks(int debounceTicks) {
        this.debounceTicks = Math.max(0, debounceTicks);
    }

    // Check if this is reset to zero
    public boolean isResetToZero() {
        return resetToZero;
    }

    // Set the reset to zero
    public void setResetToZero(boolean resetToZero) {
        this.resetToZero = resetToZero;
    }

    // Check if repeat while held is enabled
    public boolean isRepeatWhileHeld() {
        return repeatWhileHeld;
    }

    // Set the repeat while held
    public void setRepeatWhileHeld(boolean repeatWhileHeld) {
        this.repeatWhileHeld = repeatWhileHeld;
    }

    // Get the repeat interval ticks
    public int getRepeatIntervalTicks() {
        return repeatIntervalTicks;
    }

    // Set the repeat interval ticks
    public void setRepeatIntervalTicks(int repeatIntervalTicks) {
        this.repeatIntervalTicks = Math.max(1, repeatIntervalTicks);
    }

    // Check if this is pressed
    public boolean isPressed() {
        return pressed;
    }

    // Get the value
    public double getValue() {
        return value;
    }

    // Get the unsigned value
    public double getUnsignedValue() {
        return Mth.clamp(Math.abs(value), 0.0D, 1.0D);
    }

    // Get the redstone strength
    public int getRedstoneStrength() {
        return toRedstone(getUnsignedValue());
    }

    // Set the direct value
    public void setDirectValue(double value) {
        directValue = clampValue(value);
        if (mode == AnalogueChannelMode.DIRECT) {
            targetValue = directValue;
        }
    }

    // Set the value instant
    public void setValueInstant(double value) {
        double clamped = clampValue(value);
        directValue = clamped;
        targetValue = clamped;
        responseValue = clamped;
        filteredValue = clamped;
        this.value = applyDeadzone(clamped);
    }

    // Press the analogue channel
    public boolean press(long gameTick) {
        if (pressed || !canEdge(gameTick)) {
            return false;
        }
        pressed = true;
        lastEdgeTick = gameTick;
        return switch (mode) {
            case STEP -> applyStep(gameTick);
            case LATCH -> {
                latched = !latched;
                targetValue = latched ? clampValue(maxValue) : resetValue();
                yield true;
            }
            case DIRECT -> {

                double prev = directValue;
                directValue = clampValue(maxValue);
                targetValue = directValue;
                responseValue = directValue;
                filteredValue = directValue;
                value = applyDeadzone(filteredValue);
                yield Math.abs(prev - directValue) > EPSILON;
            }
            default -> true;
        };
    }

    // Release the analogue channel
    public boolean release(long gameTick) {
        if (!pressed || !canEdge(gameTick)) {
            return false;
        }
        pressed = false;
        lastEdgeTick = gameTick;
        if (mode == AnalogueChannelMode.DIRECT) {

            double prev = directValue;
            directValue = resetValue();
            targetValue = directValue;
            responseValue = directValue;
            filteredValue = directValue;
            value = applyDeadzone(filteredValue);
            return Math.abs(prev - directValue) > EPSILON;
        }
        return true;
    }

    // Tap the analogue channel
    public boolean tap(long gameTick) {
        if (!canEdge(gameTick)) {
            return false;
        }
        lastEdgeTick = gameTick;
        return switch (mode) {
            case STEP -> applyStep(gameTick);
            case LATCH -> {
                latched = !latched;
                targetValue = latched ? clampValue(maxValue) : resetValue();
                yield true;
            }
            case MOMENTARY, RAMP -> {
                double prev = value;
                responseValue = clampValue(maxValue);
                filteredValue = responseValue;
                value = applyDeadzone(filteredValue);
                if (resetToZero) {
                    targetValue = resetValue();
                }
                yield Math.abs(prev - value) > EPSILON;
            }
            case DIRECT -> {

                double prev = directValue;
                directValue = clampValue(maxValue);
                targetValue = directValue;
                responseValue = directValue;
                filteredValue = directValue;
                value = applyDeadzone(filteredValue);
                if (resetToZero) {
                    directValue = resetValue();
                    targetValue = directValue;
                }
                yield Math.abs(prev - value) > EPSILON;
            }
        };
    }

    // Step the analogue channel
    public boolean stepBy(double amount, long gameTick) {
        if (!canEdge(gameTick)) {
            return false;
        }
        double previousTarget = targetValue;
        double nextTarget = clampValue(targetValue + amount);
        targetValue = nextTarget;
        directValue = nextTarget;
        lastStepTick = gameTick;
        lastEdgeTick = gameTick;
        return Math.abs(previousTarget - nextTarget) > EPSILON;
    }

    // Reset the analogue channel
    public void reset() {
        pressed = false;
        latched = false;
        directValue = resetValue();
        targetValue = directValue;
        responseValue = directValue;
        filteredValue = directValue;
        value = directValue;
        lastStepTick = Long.MIN_VALUE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the analogue state
    public boolean tick(long gameTick) {
        double prev = value;
        updateTargetForMode(gameTick);

        if (mode == AnalogueChannelMode.DIRECT || mode == AnalogueChannelMode.LATCH) {
            responseValue = clampValue(targetValue);
            filteredValue = responseValue;
            value = applyDeadzone(filteredValue);
            return Math.abs(prev - value) > EPSILON;
        }

        responseValue = moveToward(responseValue, targetValue);
        if (smoothing > 0.0D) {
            double alpha = Mth.clamp(1.0D - smoothing, 0.05D, 1.0D);
            filteredValue += (responseValue - filteredValue) * alpha;
        } else {
            filteredValue = responseValue;
        }
        value = applyDeadzone(clampValue(filteredValue));

        if (!pressed
                && resetToZero
                && mode != AnalogueChannelMode.LATCH
                && Math.abs(targetValue - resetValue()) <= EPSILON
                && getRedstoneStrength() <= 1
                && Math.abs(value) <= (1.0D / 15.0D + EPSILON)) {
            responseValue = 0.0D;
            filteredValue = 0.0D;
            value = 0.0D;
        }

        return Math.abs(prev - value) > EPSILON;
    }

    // Describe the analogue channel
    public Map<String, Object> describe() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", mode.name().toLowerCase());
        info.put("value", value);
        info.put("redstone", getRedstoneStrength());
        info.put("min", minValue);
        info.put("max", maxValue);
        info.put("riseRate", riseRate);
        info.put("fallRate", fallRate);
        info.put("stepAmount", stepAmount);
        info.put("deadzone", deadzone);
        info.put("smoothing", smoothing);
        info.put("debounceTicks", debounceTicks);
        info.put("resetToZero", resetToZero);
        info.put("repeatWhileHeld", repeatWhileHeld);
        info.put("repeatIntervalTicks", repeatIntervalTicks);
        info.put("pressed", pressed);
        info.put("signed", signed);
        return info;
    }

    // Write the tag
    public CompoundTag writeToTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putBoolean("Signed", signed);
        tag.putString("Mode", mode.name());
        tag.putDouble("Min", minValue);
        tag.putDouble("Max", maxValue);
        tag.putDouble("RiseRate", riseRate);
        tag.putDouble("FallRate", fallRate);
        tag.putDouble("StepAmount", stepAmount);
        tag.putDouble("Deadzone", deadzone);
        tag.putDouble("Smoothing", smoothing);
        tag.putInt("DebounceTicks", debounceTicks);
        tag.putBoolean("ResetToZero", resetToZero);
        tag.putBoolean("RepeatWhileHeld", repeatWhileHeld);
        tag.putInt("RepeatIntervalTicks", repeatIntervalTicks);
        tag.putBoolean("Pressed", pressed);
        tag.putBoolean("Latched", latched);
        tag.putDouble("DirectValue", directValue);
        tag.putDouble("TargetValue", targetValue);
        tag.putDouble("ResponseValue", responseValue);
        tag.putDouble("FilteredValue", filteredValue);
        tag.putDouble("Value", value);
        return tag;
    }

    // Read the tag
    public void readFromTag(CompoundTag tag) {
        if (tag.contains("Mode")) {
            try {
                mode = AnalogueChannelMode.valueOf(tag.getString("Mode"));
            } catch (IllegalArgumentException ignored) {
                mode = AnalogueChannelMode.RAMP;
            }
        }
        minValue = tag.contains("Min") ? tag.getDouble("Min") : (signed ? -1.0D : 0.0D);
        maxValue = tag.contains("Max") ? tag.getDouble("Max") : 1.0D;
        riseRate = tag.contains("RiseRate") ? Math.max(0.0D, tag.getDouble("RiseRate")) : 0.08D;
        fallRate = tag.contains("FallRate") ? Math.max(0.0D, tag.getDouble("FallRate")) : 0.08D;
        stepAmount = tag.contains("StepAmount") ? tag.getDouble("StepAmount") : 0.1D;
        deadzone = tag.contains("Deadzone") ? Mth.clamp(tag.getDouble("Deadzone"), 0.0D, 0.95D) : 0.0D;
        smoothing = tag.contains("Smoothing") ? Mth.clamp(tag.getDouble("Smoothing"), 0.0D, 0.98D) : 0.0D;
        debounceTicks = tag.contains("DebounceTicks") ? Math.max(0, tag.getInt("DebounceTicks")) : 0;
        resetToZero = !tag.contains("ResetToZero") || tag.getBoolean("ResetToZero");
        repeatWhileHeld = tag.contains("RepeatWhileHeld") && tag.getBoolean("RepeatWhileHeld");
        repeatIntervalTicks = tag.contains("RepeatIntervalTicks") ? Math.max(1, tag.getInt("RepeatIntervalTicks")) : 4;
        pressed = tag.contains("Pressed") && tag.getBoolean("Pressed");
        latched = tag.contains("Latched") && tag.getBoolean("Latched");
        directValue = clampValue(tag.contains("DirectValue") ? tag.getDouble("DirectValue") : resetValue());
        targetValue = clampValue(tag.contains("TargetValue") ? tag.getDouble("TargetValue") : directValue);
        double storedValue = clampValue(tag.contains("Value") ? tag.getDouble("Value") : targetValue);
        responseValue = clampValue(tag.contains("ResponseValue") ? tag.getDouble("ResponseValue") : storedValue);
        filteredValue = clampValue(tag.contains("FilteredValue") ? tag.getDouble("FilteredValue") : storedValue);
        value = applyDeadzone(filteredValue);
    }

    // Convert the analogue channel to redstone
    public static int toRedstone(double strength) {
        return Mth.clamp((int) Math.round(Mth.clamp(strength, 0.0D, 1.0D) * 15.0D), 0, 15);
    }

    // Check if this can edge
    private boolean canEdge(long gameTick) {
        return lastEdgeTick == Long.MIN_VALUE || gameTick - lastEdgeTick >= debounceTicks;
    }

    // Apply the step
    private boolean applyStep(long gameTick) {
        double previousTarget = targetValue;
        double nextTarget = clampValue(targetValue + stepAmount);
        targetValue = nextTarget;
        directValue = nextTarget;
        lastStepTick = gameTick;
        return Math.abs(previousTarget - nextTarget) > EPSILON;
    }

    // Update the target for mode
    private void updateTargetForMode(long gameTick) {
        switch (mode) {
            case MOMENTARY, RAMP -> targetValue = pressed ? clampValue(maxValue) : (resetToZero ? resetValue() : targetValue);
            case STEP -> {
                if (pressed && repeatWhileHeld && (lastStepTick == Long.MIN_VALUE || gameTick - lastStepTick >= repeatIntervalTicks)) {
                    applyStep(gameTick);
                }
                if (!pressed && resetToZero) {
                    targetValue = resetValue();
                }
            }
            case LATCH -> targetValue = latched ? clampValue(maxValue) : resetValue();
            case DIRECT -> targetValue = directValue;
        }
    }

    // Move toward the target
    private double moveToward(double current, double target) {
        double delta = target - current;
        if (Math.abs(delta) <= EPSILON) {
            return target;
        }
        double step = delta > 0.0D ? riseRate : fallRate;
        if (step <= 0.0D) {
            return target;
        }
        return current + Mth.clamp(delta, -step, step);
    }

    // Reset the value
    private double resetValue() {
        return signed && minValue > 0.0D ? minValue : 0.0D;
    }

    // Clamp the value
    private double clampValue(double val) {
        double lower = Math.min(minValue, maxValue);
        double upper = Math.max(minValue, maxValue);
        if (!signed) {
            lower = Math.max(0.0D, lower);
        }
        return Mth.clamp(val, lower, upper);
    }

    // Apply the deadzone
    private double applyDeadzone(double val) {
        return Math.abs(val) <= deadzone ? 0.0D : val;
    }
}
