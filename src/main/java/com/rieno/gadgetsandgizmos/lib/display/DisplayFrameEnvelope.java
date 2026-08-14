package com.rieno.gadgetsandgizmos.lib.display;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

// Pack display data and its presentation settings without changing the original payload
public final class DisplayFrameEnvelope {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String PRESENTATION_KEY = "AccDisplayPresentation";
    public static final String PIXEL_WIDTH_KEY = "PixelWidth";
    public static final String PIXEL_HEIGHT_KEY = "PixelHeight";
    public static final String TARGET_MODE_KEY = "TargetDisplayMode";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the display frame envelope
    private DisplayFrameEnvelope() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the display frame envelope
    public static CompoundTag create(
            CompoundTag payload, CompoundTag presentation, int pixelWidth, int pixelHeight
    ) {
        CompoundTag safePayload = payload == null ? new CompoundTag() : payload;
        CompoundTag safePresentation = presentation == null ? new CompoundTag() : presentation;
        if (safePayload.isEmpty() && safePresentation.isEmpty()) {
            return new CompoundTag();
        }
        CompoundTag envelope = safePayload.copy();
        envelope.putInt(PIXEL_WIDTH_KEY, Math.max(1, pixelWidth));
        envelope.putInt(PIXEL_HEIGHT_KEY, Math.max(1, pixelHeight));
        if (!safePresentation.isEmpty()) {
            envelope.put(PRESENTATION_KEY, safePresentation.copy());
        }
        return envelope;
    }

    // Get the payload
    public static CompoundTag payload(CompoundTag envelope) {
        if (envelope == null) {
            return new CompoundTag();
        }
        CompoundTag payload = envelope.copy();
        payload.remove(PRESENTATION_KEY);
        return payload;
    }

    // Get the presentation
    public static CompoundTag presentation(CompoundTag envelope) {
        return envelope == null
                ? new CompoundTag()
                : envelope.getCompound(PRESENTATION_KEY).copy();
    }

    // Get the requested mode
    public static String requestedMode(CompoundTag presentation) {
        return presentation == null ? "" : presentation.getString(TARGET_MODE_KEY);
    }

    // Check if this has renderable payload
    public static boolean hasRenderablePayload(CompoundTag payload) {
        return payload != null && (!payload.getString("Format").isBlank()
                || payload.contains("Lines", Tag.TAG_LIST)
                || payload.contains("Terminal", Tag.TAG_COMPOUND)
                || payload.contains("Widgets", Tag.TAG_LIST));
    }
}
