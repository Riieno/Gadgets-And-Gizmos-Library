package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

// Store one mechanic binding with its label, mode, frequencies and target
public final class ControllerMechanicBinding {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Channel id
    private final String channelId;
    // Mechanic
    private final ControllerMechanic mechanic;
    // Tracks whether the axis is positive
    private final boolean positiveAxis;
    // Translation key
    private final String translationKey;
    // Controller mechanic binding mode
    private final AnalogueChannelMode mode;
    // Frequency binding
    private final FrequencyBinding frequencyBinding;
    // Direct target
    private final @Nullable ControllerDirectTargetReference directTarget;
    // Key code
    private final int keyCode;
    // Local output side
    private final @Nullable Direction localOutputSide;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller mechanic binding
    public ControllerMechanicBinding(String channelId, ControllerMechanic mechanic, boolean positiveAxis,
                                     String translationKey, AnalogueChannelMode mode, FrequencyBinding frequencyBinding,
                                     @Nullable ControllerDirectTargetReference directTarget, int keyCode,
                                     @Nullable Direction localOutputSide) {
        this.channelId = channelId == null ? "" : channelId;
        this.mechanic = mechanic;
        this.positiveAxis = positiveAxis;
        this.translationKey = translationKey == null ? "" : translationKey;
        this.mode = mode == null ? AnalogueChannelMode.RAMP : mode;
        this.frequencyBinding = copyBinding(this.channelId, frequencyBinding);
        this.directTarget = directTarget;
        this.keyCode = keyCode;
        this.localOutputSide = localOutputSide;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the channel id
    public String channelId() {
        return channelId;
    }

    // Get the mechanic
    public ControllerMechanic mechanic() {
        return mechanic;
    }

    // Check if the binding uses the positive axis
    public boolean positiveAxis() {
        return positiveAxis;
    }

    // Get the translation key
    public String translationKey() {
        return translationKey;
    }

    // Get the mode
    public AnalogueChannelMode mode() {
        return mode;
    }

    // Get the frequency binding
    public FrequencyBinding frequencyBinding() {
        return copyBinding(channelId, frequencyBinding);
    }

    // Get the direct target
    public @Nullable ControllerDirectTargetReference directTarget() {
        return directTarget;
    }

    // Handle key code
    public int keyCode() {
        return keyCode;
    }

    // Get the local output side
    public @Nullable Direction localOutputSide() {
        return localOutputSide;
    }

    // Check if this uses frequency binding
    public boolean usesFrequencyBinding() {
        return frequencyBinding.isBound();
    }

    // Check if this uses direct target
    public boolean usesDirectTarget() {
        return directTarget != null && directTarget.isBound();
    }

    // Write the controller mechanic binding data
    public CompoundTag toTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("ChannelId", channelId);
        tag.putString("Mechanic", mechanic.id());
        tag.putBoolean("PositiveAxis", positiveAxis);
        tag.putString("TranslationKey", translationKey);
        tag.putString("Mode", mode.name());
        tag.putInt("KeyCode", keyCode);
        if (localOutputSide != null) {
            tag.putString("LocalOutputSide", localOutputSide.getSerializedName());
        }
        tag.put("FrequencyBinding", frequencyBinding.toTag(provider));
        if (directTarget != null && directTarget.isBound()) {
            tag.put("DirectTarget", directTarget.toTag());
        }
        return tag;
    }

    // Read the controller mechanic binding data
    public static @Nullable ControllerMechanicBinding fromTag(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        ControllerMechanic mechanic = ControllerMechanic.byId(tag.getString("Mechanic"));
        if (mechanic == null) {
            return null;
        }
        FrequencyBinding binding = tag.contains("FrequencyBinding")
                ? FrequencyBinding.fromTag(tag.getCompound("FrequencyBinding"), provider)
                : new FrequencyBinding(tag.getString("ChannelId"));
        ControllerDirectTargetReference directTarget = tag.contains("DirectTarget")
                ? ControllerDirectTargetReference.fromTag(tag.getCompound("DirectTarget"))
                : null;
        Direction localOutputSide = tag.contains("LocalOutputSide")
                ? Direction.byName(tag.getString("LocalOutputSide"))
                : null;
        AnalogueChannelMode mode;
        try {
            mode = AnalogueChannelMode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException err) {
            mode = AnalogueChannelMode.RAMP;
        }
        return new ControllerMechanicBinding(
                tag.getString("ChannelId"),
                mechanic,
                tag.getBoolean("PositiveAxis"),
                tag.getString("TranslationKey"),
                mode,
                binding,
                directTarget,
                tag.getInt("KeyCode"),
                localOutputSide);
    }

    // Copy the binding
    private static FrequencyBinding copyBinding(String channelId, FrequencyBinding src) {
        FrequencyBinding copy = new FrequencyBinding(channelId);
        if (src != null) {
            copy.set(src.first(), src.second());
        }
        return copy;
    }
}
