package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Define the built-in SCM steering strategies independently of vehicle motion mode. */
public enum ScmSteeringMode {
    FRONT_WHEEL("front_wheel", "Front Wheel"),
    REAR_WHEEL("rear_wheel", "Rear Wheel"),
    FOUR_WHEEL("four_wheel", "4-Wheel"),
    TANK("tank", "Tank"),
    RUDDER("rudder", "Rudder"),
    CUSTOM("custom", "Custom"),
    AUTO("auto", "Auto");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double SINGLE_AXLE_EPSILON = 0.25D;
    private static final List<ScmSteeringMode> SELECTIONS = List.of(values());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FIELDS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final String id;
    private final String displayName;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SCM steering mode
    ScmSteeringMode(String id, String displayName){
        this.id = id;
        this.displayName = displayName;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the serialized id
    public String id(){
        return id;
    }

    // Get the display name
    public String displayName(){
        return displayName;
    }

    // Get the selectable modes in UI order
    public static List<ScmSteeringMode> selections(){
        return SELECTIONS;
    }

    // Resolve a serialized steering mode
    public static ScmSteeringMode fromId(@Nullable String id){
        String normalized = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        return SELECTIONS.stream().filter(mode -> mode.id.equals(normalized))
                .findFirst().orElse(AUTO);
    }

    // Check whether a serialized selection is valid
    public static boolean isSelection(@Nullable String id){
        if (id == null) return false;
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        return SELECTIONS.stream().anyMatch(mode -> mode.id.equals(normalized));
    }

    /**
     * Predict a steering strategy from authored control ownership. Differential
     * drive wins, explicit wheel axles win over generic wheel discovery, and a
     * conventional front axle is the safe default when wheel ownership is absent.
     */
    public static ScmSteeringMode predict(
            boolean differentialDrive,
            int frontWheelBindings,
            int rearWheelBindings,
            int wheelCount,
            boolean yawControl
    ){
        if (differentialDrive) return TANK;
        if (frontWheelBindings > 0 && rearWheelBindings > 0) return FOUR_WHEEL;
        if (frontWheelBindings > 0) return FRONT_WHEEL;
        if (rearWheelBindings > 0) return REAR_WHEEL;
        if (wheelCount > 0) return FRONT_WHEEL;
        return yawControl ? RUDDER : CUSTOM;
    }

    // Check whether this mode controls wheel hubs from their axle geometry
    public boolean usesWheelGeometry(){
        return this == FRONT_WHEEL || this == REAR_WHEEL || this == FOUR_WHEEL;
    }

    // Resolve one wheel hub's signed steering demand from its longitudinal position
    public double wheelDemand(
            double steeringDemand,
            double longitudinalPosition,
            double trailingPosition,
            double leadingPosition
    ){
        double demand = finite(steeringDemand);
        double position = finite(longitudinalPosition);
        double trailing = finite(trailingPosition);
        double leading = finite(leadingPosition);
        boolean singleAxle = leading - trailing <= SINGLE_AXLE_EPSILON;
        boolean front = singleAxle ? leading >= 0.0D
                : position >= (trailing + leading) * 0.5D;
        boolean rear = singleAxle || !front;
        return switch (this) {
            case FRONT_WHEEL -> front ? demand : 0.0D;
            case REAR_WHEEL -> rear ? -demand : 0.0D;
            case FOUR_WHEEL -> front ? demand : -demand;
            default -> 0.0D;
        };
    }

    // Check whether this mode suppresses propulsion while yaw is active
    public boolean cutsLongitudinalPower(double yawDemand){
        return this == TANK && ScmTankSteering.isTurning(yawDemand);
    }

    // Get the equivalent bicycle steering angle used by ground route planning
    public double planningSteeringRadians(double physicalSteeringRadians){
        double physical = Math.max(Math.toRadians(3.0D), Math.min(
                Math.toRadians(70.0D), Math.abs(finite(physicalSteeringRadians))));
        if (this == FOUR_WHEEL) {
            return Math.min(Math.toRadians(70.0D),
                    Math.atan(Math.tan(physical) * 2.0D));
        }
        return this == TANK ? Math.toRadians(70.0D) : physical;
    }

    // Normalize a finite scalar
    private static double finite(double value){
        return Double.isFinite(value) ? value : 0.0D;
    }
}
