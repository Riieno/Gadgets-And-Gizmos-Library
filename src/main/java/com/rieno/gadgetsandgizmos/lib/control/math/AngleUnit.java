package com.rieno.gadgetsandgizmos.lib.control.math;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Convert signed angles and evaluate trigonometry in the selected unit without wrapping
public enum AngleUnit{
    RADIANS, DEGREES, TURNS, GRADIANS;

    public String id(){ return name().toLowerCase(Locale.ROOT); }

    public static List<String> optionIds(){
        return Arrays.stream(values()).map(AngleUnit::id).toList();
    }

    // Keep radians when a saved value is absent or unrecognized
    public static AngleUnit fromId(String id){
        return switch(id == null ? "" : id.strip().toLowerCase(Locale.ROOT)){
            case "degrees" -> DEGREES;
            case "turns" -> TURNS;
            case "gradians" -> GRADIANS;
            default -> RADIANS;
        };
    }

    public double toRadians(double angle){
        return switch(this){
            case RADIANS -> angle;
            case DEGREES -> Math.toRadians(angle);
            case TURNS -> angle * (2.0D * Math.PI);
            case GRADIANS -> angle * (Math.PI / 200.0D);
        };
    }

    public double fromRadians(double angle){
        return switch(this){
            case RADIANS -> angle;
            case DEGREES -> Math.toDegrees(angle);
            case TURNS -> angle / (2.0D * Math.PI);
            case GRADIANS -> angle * (200.0D / Math.PI);
        };
    }

    // Forward functions consume an angle and return a dimensionless ratio
    public double sin(double angle){ return Math.sin(toRadians(angle)); }
    public double cos(double angle){ return Math.cos(toRadians(angle)); }
    public double tan(double angle){ return Math.tan(toRadians(angle)); }

    // Inverse functions consume ratios and return an angle in this unit
    public double asin(double val){ return fromRadians(Math.asin(val)); }
    public double acos(double val){ return fromRadians(Math.acos(val)); }
    public double atan(double val){ return fromRadians(Math.atan(val)); }
    public double atan2(double y, double x){ return fromRadians(Math.atan2(y, x)); }
}
