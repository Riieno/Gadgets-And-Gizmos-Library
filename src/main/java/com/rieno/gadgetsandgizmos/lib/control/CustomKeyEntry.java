package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.UUID;

// Store the configuration for one custom controller key binding
public class CustomKeyEntry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Custom key entry id
    private final String id;
    // Current key code
    public int keyCode = -1;
    // Current step down key code
    public int stepDownKeyCode = -1;
    // Current display label
    public String label = "Custom";
    // Current alias
    public String alias = "";
    // Current custom key entry mode
    public AnalogueChannelMode mode = AnalogueChannelMode.RAMP;
    // Rise rate
    public double riseRate = 0.08;
    // Fall rate
    public double fallRate = 0.08;
    // Current step amount
    public double stepAmount = 0.1;
    // Current step down amount
    public double stepDownAmount = 0.1;
    // Current deadzone
    public double deadzone = 0.0;
    // Current smoothing
    public double smoothing = 0.2;
    // Local output side
    public Direction localOutputSide = null;
    // Current first entry
    public ItemStack first = ItemStack.EMPTY;
    // Current second entry
    public ItemStack second = ItemStack.EMPTY;
    // Input first
    public ItemStack inputFirst = ItemStack.EMPTY;
    // Input second
    public ItemStack inputSecond = ItemStack.EMPTY;
    // Current direct target
    public ControllerDirectTargetReference directTarget = null;
    // Input target
    public ControllerDirectTargetReference inputTarget = null;
    // Current binding preset
    public String bindingPreset = "none";
    // Current binding owner
    public ControllerBindingOwner owner = ControllerBindingOwner.USER;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the custom key entry
    public CustomKeyEntry(String id) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the new
    public static CustomKeyEntry createNew() {
        return new CustomKeyEntry(UUID.randomUUID().toString());
    }

    // Get the id
    public String id() {
        return id;
    }

    // Write the custom key entry data
    public CompoundTag toTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putInt("KeyCode", keyCode);
        tag.putInt("StepDownKeyCode", stepDownKeyCode);
        tag.putString("Label", label == null ? "Custom" : label);
        tag.putString("Alias", alias == null ? "" : alias);
        tag.putString("Mode", mode == null ? "ramp" : mode.name().toLowerCase(Locale.ROOT));
        tag.putFloat("RiseRate", (float) riseRate);
        tag.putFloat("FallRate", (float) fallRate);
        tag.putFloat("StepAmount", (float) stepAmount);
        tag.putFloat("StepDownAmount", (float) stepDownAmount);
        tag.putFloat("Deadzone", (float) deadzone);
        tag.putFloat("Smoothing", (float) smoothing);
        tag.putString("LocalSide", localOutputSide == null ? "" : localOutputSide.getSerializedName());
        if (first != null && !first.isEmpty()) tag.put("First", first.saveOptional(provider));
        if (second != null && !second.isEmpty()) tag.put("Second", second.saveOptional(provider));
        if (inputFirst != null && !inputFirst.isEmpty()) tag.put("InputFirst", inputFirst.saveOptional(provider));
        if (inputSecond != null && !inputSecond.isEmpty()) tag.put("InputSecond", inputSecond.saveOptional(provider));
        if (directTarget != null && directTarget.isBound()) tag.put("DirectTarget", directTarget.toTag());
        if (inputTarget != null && inputTarget.isBound()) tag.put("InputTarget", inputTarget.toTag());
        tag.putString("BindingPreset", bindingPreset == null || bindingPreset.isBlank() ? "none" : bindingPreset);
        tag.putString("Owner", owner == null
                ? ControllerBindingOwner.USER.id() : owner.id());
        return tag;
    }

    // Read the custom key entry data
    public static CustomKeyEntry fromTag(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag == null || !tag.contains("Id")) return null;
        String id = tag.getString("Id");
        if (id.isBlank()) return null;
        CustomKeyEntry entry = new CustomKeyEntry(id);
        entry.keyCode = tag.contains("KeyCode") ? tag.getInt("KeyCode") : -1;
        entry.stepDownKeyCode = tag.contains("StepDownKeyCode") ? tag.getInt("StepDownKeyCode") : -1;
        entry.label = tag.contains("Label") ? tag.getString("Label") : "Custom";
        entry.alias = tag.contains("Alias") ? tag.getString("Alias") : "";
        if (tag.contains("Mode")) {
            try {
                entry.mode = AnalogueChannelMode.valueOf(tag.getString("Mode").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                entry.mode = AnalogueChannelMode.RAMP;
            }
        }
        entry.riseRate = tag.contains("RiseRate") ? tag.getFloat("RiseRate") : 0.08;
        entry.fallRate = tag.contains("FallRate") ? tag.getFloat("FallRate") : 0.08;
        entry.stepAmount = tag.contains("StepAmount") ? tag.getFloat("StepAmount") : 0.1;
        entry.stepDownAmount = tag.contains("StepDownAmount") ? tag.getFloat("StepDownAmount") : entry.stepAmount;
        entry.deadzone = tag.contains("Deadzone") ? tag.getFloat("Deadzone") : 0.0;
        entry.smoothing = tag.contains("Smoothing") ? tag.getFloat("Smoothing") : 0.2;
        entry.localOutputSide = tag.contains("LocalSide") && !tag.getString("LocalSide").isBlank()
                ? Direction.byName(tag.getString("LocalSide")) : null;
        entry.first = tag.contains("First")
                ? normalize(ItemStack.parseOptional(provider, tag.getCompound("First"))) : ItemStack.EMPTY;
        entry.second = tag.contains("Second")
                ? normalize(ItemStack.parseOptional(provider, tag.getCompound("Second"))) : ItemStack.EMPTY;
        entry.inputFirst = tag.contains("InputFirst")
            ? normalize(ItemStack.parseOptional(provider, tag.getCompound("InputFirst"))) : ItemStack.EMPTY;
        entry.inputSecond = tag.contains("InputSecond")
            ? normalize(ItemStack.parseOptional(provider, tag.getCompound("InputSecond"))) : ItemStack.EMPTY;
        entry.directTarget = tag.contains("DirectTarget")
                ? ControllerDirectTargetReference.fromTag(tag.getCompound("DirectTarget")) : null;
        entry.inputTarget = tag.contains("InputTarget")
            ? ControllerDirectTargetReference.fromTag(tag.getCompound("InputTarget")) : null;
        entry.bindingPreset = tag.contains("BindingPreset") && !tag.getString("BindingPreset").isBlank()
            ? tag.getString("BindingPreset") : "none";
        entry.owner = ControllerBindingOwner.fromId(tag.getString("Owner"));
        return entry;
    }

    // Normalize the custom key entry
    private static ItemStack normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
