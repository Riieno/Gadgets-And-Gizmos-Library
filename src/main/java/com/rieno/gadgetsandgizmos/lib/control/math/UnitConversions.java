package com.rieno.gadgetsandgizmos.lib.control.math;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Convert compatible physical and Minecraft units using nominal twenty-tick seconds
public final class UnitConversions{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final double TICKS_PER_SECOND = 20.0D;
    public static final double METERS_PER_BLOCK = 1.0D;
    public static final double LITERS_PER_BUCKET = 1.0D;
    public static final double MILLIBUCKETS_PER_BUCKET = 1000.0D;
    private static final double LITERS_PER_US_GALLON = 3.785411784D;
    private static final double LITERS_PER_IMPERIAL_GALLON = 4.54609D;

    public enum Category{
        TIME, DISTANCE, SPEED, WEIGHT, FLUID_VOLUME, POWER;

        public String id(){ return name().toLowerCase(Locale.ROOT); }
        public String label(){ return displayLabel(id()); }
        public static List<String> optionIds(){ return Arrays.stream(values()).map(Category::id).toList(); }
        public static Category fromId(String id){
            return Arrays.stream(values()).filter(category -> category.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown unit category: " + id));
        }
    }

    // Physical factors use SI bases; electrical dimensions and FE require ElectricalUnitConversions
    public record Unit(String id, String symbol, Category category, double factor){
        public Unit{
            if(id == null || id.isBlank() || symbol == null || category == null || !Double.isFinite(factor) || factor <= 0){
                throw new IllegalArgumentException("A unit requires an id, category and positive finite factor");
            }
        }

        public String label(){ return displayLabel(id); }
    }

    private static final List<Unit> UNITS = List.of(
            new Unit("ticks", "ticks", Category.TIME, 1.0D / TICKS_PER_SECOND),
            new Unit("seconds", "s", Category.TIME, 1),
            new Unit("minutes", "min", Category.TIME, 60),
            new Unit("hours", "h", Category.TIME, 3600),
            new Unit("milliseconds", "ms", Category.TIME, 0.001),
            new Unit("days", "d", Category.TIME, 86400),
            new Unit("weeks", "wk", Category.TIME, 604800),
            new Unit("microseconds", "us", Category.TIME, 0.000001),
            new Unit("nanoseconds", "ns", Category.TIME, 0.000000001),
            new Unit("meters", "m", Category.DISTANCE, 1),
            new Unit("kilometers", "km", Category.DISTANCE, 1000),
            new Unit("miles", "mi", Category.DISTANCE, 1609.344),
            new Unit("yards", "yd", Category.DISTANCE, 0.9144),
            new Unit("blocks", "blocks", Category.DISTANCE, METERS_PER_BLOCK),
            new Unit("feet", "ft", Category.DISTANCE, 0.3048),
            new Unit("inches", "in", Category.DISTANCE, 0.0254),
            new Unit("centimeters", "cm", Category.DISTANCE, 0.01),
            new Unit("millimeters", "mm", Category.DISTANCE, 0.001),
            new Unit("micrometers", "um", Category.DISTANCE, 0.000001),
            new Unit("nautical_miles", "nmi", Category.DISTANCE, 1852),
            new Unit("meters_per_second", "m/s", Category.SPEED, 1),
            new Unit("kilometers_per_second", "km/s", Category.SPEED, 1000),
            new Unit("kilometers_per_hour", "km/h", Category.SPEED, 1000.0D / 3600),
            new Unit("miles_per_hour", "mph", Category.SPEED, 1609.344D / 3600),
            new Unit("knots", "kn", Category.SPEED, 1852.0D / 3600),
            new Unit("blocks_per_second", "blocks/s", Category.SPEED, METERS_PER_BLOCK),
            new Unit("blocks_per_tick", "blocks/tick", Category.SPEED, METERS_PER_BLOCK * TICKS_PER_SECOND),
            new Unit("feet_per_second", "ft/s", Category.SPEED, 0.3048),
            new Unit("yards_per_second", "yd/s", Category.SPEED, 0.9144),
            new Unit("meters_per_minute", "m/min", Category.SPEED, 1.0D / 60),
            new Unit("blocks_per_minute", "blocks/min", Category.SPEED, METERS_PER_BLOCK / 60),
            new Unit("grams", "g", Category.WEIGHT, 0.001),
            new Unit("kilograms", "kg", Category.WEIGHT, 1),
            new Unit("tonnes", "t", Category.WEIGHT, 1000),
            new Unit("milligrams", "mg", Category.WEIGHT, 0.000001),
            new Unit("micrograms", "ug", Category.WEIGHT, 0.000000001),
            new Unit("ounces", "oz", Category.WEIGHT, 0.028349523125),
            new Unit("pounds", "lb", Category.WEIGHT, 0.45359237),
            new Unit("stone", "st", Category.WEIGHT, 6.35029318),
            new Unit("us_short_tons", "US ton", Category.WEIGHT, 907.18474),
            new Unit("uk_long_tons", "UK ton", Category.WEIGHT, 1016.0469088),
            new Unit("buckets", "B", Category.FLUID_VOLUME, LITERS_PER_BUCKET),
            new Unit("millibuckets", "mB", Category.FLUID_VOLUME, LITERS_PER_BUCKET / MILLIBUCKETS_PER_BUCKET),
            new Unit("liters", "L", Category.FLUID_VOLUME, 1),
            new Unit("milliliters", "mL", Category.FLUID_VOLUME, 0.001),
            new Unit("microliters", "uL", Category.FLUID_VOLUME, 0.000001),
            new Unit("cubic_meters", "m3", Category.FLUID_VOLUME, 1000),
            new Unit("cubic_centimeters", "cm3", Category.FLUID_VOLUME, 0.001),
            new Unit("us_fluid_ounces", "US fl oz", Category.FLUID_VOLUME, LITERS_PER_US_GALLON / 128),
            new Unit("imperial_fluid_ounces", "Imp fl oz", Category.FLUID_VOLUME, LITERS_PER_IMPERIAL_GALLON / 160),
            new Unit("us_gallons", "US gal", Category.FLUID_VOLUME, LITERS_PER_US_GALLON),
            new Unit("imperial_gallons", "Imp gal", Category.FLUID_VOLUME, LITERS_PER_IMPERIAL_GALLON),
            new Unit("us_quarts", "US qt", Category.FLUID_VOLUME, LITERS_PER_US_GALLON / 4),
            new Unit("imperial_quarts", "Imp qt", Category.FLUID_VOLUME, LITERS_PER_IMPERIAL_GALLON / 4),
            new Unit("us_pints", "US pt", Category.FLUID_VOLUME, LITERS_PER_US_GALLON / 8),
            new Unit("imperial_pints", "Imp pt", Category.FLUID_VOLUME, LITERS_PER_IMPERIAL_GALLON / 8),
            new Unit("us_cups", "US cup", Category.FLUID_VOLUME, LITERS_PER_US_GALLON / 16),
            new Unit("metric_cups", "metric cup", Category.FLUID_VOLUME, 0.25),
            new Unit("us_tablespoons", "US tbsp", Category.FLUID_VOLUME, LITERS_PER_US_GALLON / 256),
            new Unit("us_teaspoons", "US tsp", Category.FLUID_VOLUME, LITERS_PER_US_GALLON / 768),
            new Unit("fe", "FE", Category.POWER, 1),
            new Unit("fe_per_tick", "FE/t", Category.POWER, 1),
            new Unit("fe_per_second", "FE/s", Category.POWER, 1),
            new Unit("watts", "W", Category.POWER, 1),
            new Unit("milliwatts", "mW", Category.POWER, 0.001),
            new Unit("kilowatts", "kW", Category.POWER, 1000),
            new Unit("megawatts", "MW", Category.POWER, 1000000),
            new Unit("joules", "J", Category.POWER, 1),
            new Unit("kilojoules", "kJ", Category.POWER, 1000),
            new Unit("megajoules", "MJ", Category.POWER, 1000000),
            new Unit("watt_hours", "Wh", Category.POWER, 3600),
            new Unit("milliwatt_hours", "mWh", Category.POWER, 3.6),
            new Unit("kilowatt_hours", "kWh", Category.POWER, 3600000),
            new Unit("megawatt_hours", "MWh", Category.POWER, 3600000000.0D),
            new Unit("amps", "A", Category.POWER, 1),
            new Unit("milliamps", "mA", Category.POWER, 0.001),
            new Unit("microamps", "uA", Category.POWER, 0.000001),
            new Unit("kiloamps", "kA", Category.POWER, 1000),
            new Unit("amp_hours", "Ah", Category.POWER, 3600),
            new Unit("milliamp_hours", "mAh", Category.POWER, 3.6),
            new Unit("microamp_hours", "uAh", Category.POWER, 0.0036),
            new Unit("coulombs", "C", Category.POWER, 1));

    private UnitConversions(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static List<Unit> units(Category category){
        return UNITS.stream().filter(unit -> unit.category() == category).toList();
    }

    public static List<String> optionIds(Category category){ return units(category).stream().map(Unit::id).toList(); }

    public static Unit unit(String id){
        return UNITS.stream().filter(unit -> unit.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown unit: " + id));
    }

    // Keep readable labels separate from stable saved identifiers
    public static String label(String id){
        return displayLabel(id);
    }

    public static double convert(double val, String inputUnit, String outputUnit){
        return convert(val, unit(inputUnit), unit(outputUnit));
    }

    // Unit ratios avoid overflowing an intermediate value in the base unit
    public static double convert(double val, Unit inputUnit, Unit outputUnit){
        if(!Double.isFinite(val) || inputUnit == null || outputUnit == null || inputUnit.category() != outputUnit.category()){
            throw new IllegalArgumentException("Conversion requires a finite value and compatible units");
        }
        if(inputUnit.category() == Category.POWER) return ElectricalUnitConversions.convert(val, inputUnit.id(), outputUnit.id(),
                Double.NaN, Double.NaN, Double.NaN);
        double res = inputUnit.equals(outputUnit) ? val : val * (inputUnit.factor() / outputUnit.factor());
        if(!Double.isFinite(res)) throw new IllegalArgumentException("Converted value is not finite");
        return res;
    }

    private static String displayLabel(String id){
        if(id == null || id.isBlank()) return "";
        return Arrays.stream(id.split("_")).filter(word -> !word.isEmpty()).map(word -> switch(word){
            case "us", "uk", "fe" -> word.toUpperCase(Locale.ROOT);
            default -> Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }).collect(java.util.stream.Collectors.joining(" "));
    }
}
