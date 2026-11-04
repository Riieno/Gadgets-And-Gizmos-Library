package com.rieno.gadgetsandgizmos.lib.control.math;

// Convert electrical quantities using explicit voltage, duration and FE bridge context
public final class ElectricalUnitConversions{
    private ElectricalUnitConversions(){}

    public enum Dimension{
        ENERGY(0, 0), POWER(0, 1), CURRENT(1, 1), CHARGE(1, 0);

        private final int voltage, duration;
        Dimension(int voltage, int duration){ this.voltage = voltage; this.duration = duration; }
        public int voltageExponent(){ return voltage; }
        public int durationExponent(){ return duration; }
    }

    public static Dimension dimension(String unit){
        return switch(unit){
            case "fe", "joules", "kilojoules", "megajoules", "watt_hours", "milliwatt_hours", "kilowatt_hours", "megawatt_hours" -> Dimension.ENERGY;
            case "fe_per_tick", "fe_per_second", "watts", "milliwatts", "kilowatts", "megawatts" -> Dimension.POWER;
            case "amps", "milliamps", "microamps", "kiloamps" -> Dimension.CURRENT;
            case "amp_hours", "milliamp_hours", "microamp_hours", "coulombs" -> Dimension.CHARGE;
            default -> throw new IllegalArgumentException("Unknown electrical unit: " + unit);
        };
    }

    public static double factor(String id, double fePerWatt){
        dimension(id);
        if(id.equals("fe") || id.startsWith("fe_per_")){
            positive(fePerWatt);
            return 1.0D / (fePerWatt * (id.equals("fe_per_tick") ? 1 : UnitConversions.TICKS_PER_SECOND));
        }
        var unit = UnitConversions.unit(id);
        if(unit.category() != UnitConversions.Category.POWER) throw new IllegalArgumentException("An electrical unit is required");
        return unit.factor();
    }

    // Require context only when the selected dimensions actually depend on it
    public static double convert(double val, String inputUnit, String outputUnit, double fePerWatt, double volts, double seconds){
        if(!Double.isFinite(val)) throw new IllegalArgumentException("A finite value is required");
        Dimension from = dimension(inputUnit), to = dimension(outputUnit);
        if(inputUnit.equals(outputUnit)) return val;
        double ratio = factor(inputUnit, fePerWatt) / factor(outputUnit, fePerWatt);
        int voltage = from.voltage - to.voltage, duration = from.duration - to.duration;
        if(voltage != 0){ positive(volts); ratio *= voltage > 0 ? volts : 1.0D / volts; }
        if(duration != 0){ positive(seconds); ratio *= duration > 0 ? seconds : 1.0D / seconds; }
        double res = val * ratio;
        if(!Double.isFinite(res)) throw new IllegalArgumentException("Converted value is not finite");
        return res;
    }

    private static void positive(double val){
        if(!Double.isFinite(val) || val <= 0) throw new IllegalArgumentException("Positive finite electrical context is required");
    }
}
