package com.rieno.gadgetsandgizmos.lib.display;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.List;
import java.util.Locale;
import java.util.Map;

// Keep the display mode ids shared by ship data sources and their connected screens
public final class ShipInformationDisplayModes {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String DEFAULT = "passenger_information/detailed_with_schedule";
    public static final String PASSENGER_RUNNING_TEXT = "passenger_information/running_text";
    public static final String TRAIN_DESTINATION_SIMPLE = "train_destination/simple";
    public static final String TRAIN_DESTINATION_EXTENDED = "train_destination/extended";
    public static final String TRAIN_DESTINATION_DETAILED = "train_destination/detailed";
    public static final String PLATFORM_RUNNING_TEXT = "platform/running_text";
    public static final String PLATFORM_TABLE = "platform/table";
    public static final String PLATFORM_FOCUS = "platform/focus";
    public static final String DEPARTURE_BOARD_TABLE = "departure_board/table";
    public static final String STATIC_TEXT_SIMPLE = "static_text/simple_text";
    public static final String STATIC_TEXT_RICH = "static_text/rich_text";

    private static final List<String> IDS = List.of(
            PASSENGER_RUNNING_TEXT,
            DEFAULT,
            TRAIN_DESTINATION_SIMPLE,
            TRAIN_DESTINATION_EXTENDED,
            TRAIN_DESTINATION_DETAILED,
            PLATFORM_RUNNING_TEXT,
            PLATFORM_TABLE,
            PLATFORM_FOCUS,
            DEPARTURE_BOARD_TABLE,
            STATIC_TEXT_SIMPLE,
            STATIC_TEXT_RICH);
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(PASSENGER_RUNNING_TEXT, "Passenger Information - Running Text"),
            Map.entry(DEFAULT, "Passenger Information - Schedule"),
            Map.entry(TRAIN_DESTINATION_SIMPLE, "Train Destination - Simple"),
            Map.entry(TRAIN_DESTINATION_EXTENDED, "Train Destination - Extended"),
            Map.entry(TRAIN_DESTINATION_DETAILED, "Train Destination - Detailed"),
            Map.entry(PLATFORM_RUNNING_TEXT, "Platform - Running Text"),
            Map.entry(PLATFORM_TABLE, "Platform - Table"),
            Map.entry(PLATFORM_FOCUS, "Platform - Focus"),
            Map.entry(DEPARTURE_BOARD_TABLE, "Departure Board - Table"),
            Map.entry(STATIC_TEXT_SIMPLE, "Static Text - Simple"),
            Map.entry(STATIC_TEXT_RICH, "Static Text - Rich"));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship information display modes
    private ShipInformationDisplayModes() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the ids
    public static List<String> ids() {
        return IDS;
    }

    // Check if this contains the value
    public static boolean contains(String mode) {
        return IDS.contains(normalizeKey(mode));
    }

    // Normalize the ship information display modes
    public static String normalize(String mode) {
        String normalized = normalizeKey(mode);
        return IDS.contains(normalized) ? normalized : DEFAULT;
    }

    // Get the label
    public static String label(String mode) {
        return LABELS.getOrDefault(normalize(mode), LABELS.get(DEFAULT));
    }

    // Check if this is static text
    public static boolean isStaticText(String mode) {
        String normalized = normalizeKey(mode);
        return STATIC_TEXT_SIMPLE.equals(normalized) || STATIC_TEXT_RICH.equals(normalized);
    }

    // Check if this mode needs an active ship service
    public static boolean requiresActiveService(String mode) {
        return !isStaticText(mode);
    }

    // Check if this mode is currently available
    public static boolean isAvailable(String mode, boolean scheduleActive, boolean pilotPresent) {
        return !requiresActiveService(mode) || scheduleActive && pilotPresent;
    }

    // Normalize the key
    private static String normalizeKey(String mode) {
        return mode == null ? "" : mode.strip().toLowerCase(Locale.ROOT);
    }
}
