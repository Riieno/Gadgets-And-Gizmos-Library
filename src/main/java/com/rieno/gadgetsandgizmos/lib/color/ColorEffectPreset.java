package com.rieno.gadgetsandgizmos.lib.color;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Immutable, bounded color stops shared by editors, saved presets and animated effects
public record ColorEffectPreset(Mode mode, int color, List<Stop> stops, boolean animated, boolean additive){
    public enum Mode{ DEFAULT, SOLID, GRADIENT, CYCLE }
    public record Stop(float position, int color){
        public Stop{
            if(!Float.isFinite(position)) throw new IllegalArgumentException("Color stop position must be finite");
            position = Math.clamp(position, 0, 1);
            color &= 0xFFFFFF;
        }
    }
    public static final int MIN_STOPS = 2;
    public static final int MAX_STOPS = 16;
    public static final List<Stop> DEFAULT_STOPS = List.of(new Stop(0, 0xFFFFFF), new Stop(1, 0));
    public static final ColorEffectPreset DEFAULT = new ColorEffectPreset(Mode.DEFAULT, 0xFFFFFF, DEFAULT_STOPS);
    public static final Codec<ColorEffectPreset> CODEC = CompoundTag.CODEC.xmap(ColorEffectPreset::read, ColorEffectPreset::write);

    // Preserve the animated behavior of presets created before the animation setting existed
    public ColorEffectPreset(Mode mode, int color, List<Stop> stops){ this(mode, color, stops, true); }

    // Existing callers retain ordinary alpha blending
    public ColorEffectPreset(Mode mode, int color, List<Stop> stops, boolean animated){ this(mode, color, stops, animated, false); }

    public ColorEffectPreset{
        if(mode == null || stops == null || stops.size() < MIN_STOPS || stops.size() > MAX_STOPS){
            throw new IllegalArgumentException("A color preset needs 2 to 16 stops");
        }
        color &= 0xFFFFFF;
        var sorted = new ArrayList<>(stops);
        sorted.sort(Comparator.comparingDouble(Stop::position));
        sorted.set(0, new Stop(0, sorted.getFirst().color));
        sorted.set(sorted.size() - 1, new Stop(1, sorted.getLast().color));
        for(int idx = 1; idx < sorted.size(); idx++){
            if(sorted.get(idx).position <= sorted.get(idx - 1).position) throw new IllegalArgumentException("Color stops must have distinct positions");
        }
        stops = List.copyOf(sorted);
    }

    public boolean active(){ return mode != Mode.DEFAULT; }
    public boolean usesStops(){ return mode == Mode.GRADIENT || mode == Mode.CYCLE; }
    public ColorEffectPreset withMode(Mode value){ return new ColorEffectPreset(value, color, stops, animated, additive); }
    public ColorEffectPreset withColor(int value){ return new ColorEffectPreset(mode, value, stops, animated, additive); }
    public ColorEffectPreset withAnimated(boolean value){ return new ColorEffectPreset(mode, color, stops, value, additive); }

    public ColorEffectPreset withAdditive(boolean value){ return new ColorEffectPreset(mode, color, stops, animated, value); }

    // Preserve supplied color order; repeated equal colors produce a solid preset
    public static ColorEffectPreset fromColors(List<Integer> colors, boolean additive){
        if(colors.size() > MAX_STOPS) throw new IllegalArgumentException("A color preset accepts at most 16 colors");
        if(colors.isEmpty()) return DEFAULT.withAdditive(additive);
        int first = colors.getFirst() & 0xFFFFFF;
        if(colors.stream().allMatch(color -> (color & 0xFFFFFF) == first)){
            return DEFAULT.withMode(Mode.SOLID).withColor(first).withAdditive(additive);
        }
        var stops = new ArrayList<Stop>(colors.size());
        for(int index = 0; index < colors.size(); index++){
            stops.add(new Stop(index / (float)(colors.size() - 1), colors.get(index)));
        }
        return new ColorEffectPreset(Mode.GRADIENT, first, stops).withAdditive(additive);
    }

    public int samplePosition(float progress){
        if(!usesStops()) return color;
        float value = Float.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
        Stop previous = stops.getFirst();
        for(int idx = 1; idx < stops.size(); idx++){
            Stop next = stops.get(idx);
            if(value <= next.position && next.position > previous.position){
                return ColorGradient.blend(previous.color, next.color, (value - previous.position) / (next.position - previous.position));
            }
            previous = next;
        }
        return previous.color;
    }

    // Match seasonal cycle timing; gradients also travel along the exhaust and its particle lifetime
    public int sample(double time, float progress, int age){
        if(!usesStops()) return color;
        if(mode == Mode.GRADIENT && !animated) return samplePosition(progress);
        double phase = time / 240.0D + (mode == Mode.GRADIENT ? progress * .9D + age * .008D : 0);
        return samplePosition((float)(phase - Math.floor(phase)));
    }

    public CompoundTag write(){
        var tag = new CompoundTag();
        tag.putString("Mode", mode.name());
        tag.putInt("Color", color);
        tag.putBoolean("Animated", animated);
        tag.putBoolean("Additive", additive);
        var rows = new ListTag();
        for(var stop : stops){
            var row = new CompoundTag();
            row.putFloat("Position", stop.position);
            row.putInt("Color", stop.color);
            rows.add(row);
        }
        tag.put("Stops", rows);
        return tag;
    }

    public static ColorEffectPreset read(CompoundTag tag){
        if(tag == null || !tag.contains("Mode")) return DEFAULT;
        try{
            Mode mode = Mode.valueOf(tag.getString("Mode"));
            var rows = tag.getList("Stops", Tag.TAG_COMPOUND);
            if(rows.size() < MIN_STOPS || rows.size() > MAX_STOPS) return DEFAULT;
            var stops = new ArrayList<Stop>();
            for(int idx = 0; idx < rows.size(); idx++){
                var row = rows.getCompound(idx);
                stops.add(new Stop(row.getFloat("Position"), row.getInt("Color")));
            }
            return new ColorEffectPreset(mode, tag.getInt("Color"), stops, !tag.contains("Animated") || tag.getBoolean("Animated"), tag.getBoolean("Additive"));
        }catch(IllegalArgumentException ignored){ return DEFAULT; }
    }
}
