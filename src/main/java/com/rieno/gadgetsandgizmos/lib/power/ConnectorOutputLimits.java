package com.rieno.gadgetsandgizmos.lib.power;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;

// Persist an independent electrical output limit for each attachment face
public final class ConnectorOutputLimits{
    public static final double DEFAULT_WATTS = 1000;
    public static final double MAX_WATTS = 1.0E9;
    public static final double DEFAULT_VOLTS = 240;
    public static final double MAX_VOLTS = 1.0E6;
    private final EnumMap<Direction, Double> limits = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Double> voltages = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> polarities = new EnumMap<>(Direction.class);

    public double watts(Direction face){ return limits.getOrDefault(face, DEFAULT_WATTS); }
    public double volts(Direction face){ return voltages.getOrDefault(face, DEFAULT_VOLTS); }
    public int polarity(Direction face){ return polarities.getOrDefault(face, 1); }
    public double signedVolts(Direction face){ return volts(face) * polarity(face); }

    public boolean setPolarity(Direction face, int sign){
        if(face == null || sign != -1 && sign != 1) return false;
        polarities.put(face, sign);
        return true;
    }

    public boolean setWatts(Direction face, double watts){
        if(face == null || !Double.isFinite(watts) || watts < 0 || watts > MAX_WATTS) return false;
        limits.put(face, watts);
        return true;
    }

    public boolean setVolts(Direction face, double volts){
        if(face == null || !Double.isFinite(volts) || volts < 0 || volts > MAX_VOLTS) return false;
        voltages.put(face, volts);
        return true;
    }

    public void read(CompoundTag tag){
        limits.clear();
        voltages.clear();
        polarities.clear();
        for(var face : Direction.values()) if(tag.contains(face.getName())) setWatts(face, tag.getDouble(face.getName()));
        var saved = tag.getCompound("Voltages");
        for(var face : Direction.values()) if(saved.contains(face.getName())) setVolts(face, saved.getDouble(face.getName()));
        var signs = tag.getCompound("Polarities");
        for(var face : Direction.values()) if(signs.contains(face.getName())) setPolarity(face, signs.getInt(face.getName()));
    }

    public CompoundTag write(){
        var tag = new CompoundTag();
        limits.forEach((face, watts) -> tag.putDouble(face.getName(), watts));
        var saved = new CompoundTag();
        voltages.forEach((face, volts) -> saved.putDouble(face.getName(), volts));
        tag.put("Voltages", saved);
        var signs = new CompoundTag();
        polarities.forEach((face, sign) -> signs.putInt(face.getName(), sign));
        tag.put("Polarities", signs);
        return tag;
    }
}
