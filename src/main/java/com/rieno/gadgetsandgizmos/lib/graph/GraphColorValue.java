package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.color.ColorRamp;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Carry sampled gradients through element bindings without replacing their scalar defaults
public final class GraphColorValue{
    public static final String GRADIENTS = "ColorGradients";
    public static final List<String> PROPERTIES = List.of("Color", "BackgroundColor", "AccentColor", "GradientColor", "TrackColor", "BorderColor");

    private GraphColorValue(){}

    // Read ordered numeric samples from gradient maps or lists
    public static Optional<ColorRamp> gradient(GraphValue val){
        if(val == null) return Optional.empty();
        List<GraphValue> entries;
        if("list".equals(val.type())) entries = val.entries();
        else if("map".equals(val.type()) && val.value() instanceof Map<?, ?> map){
            if(map.isEmpty() || map.size() > 256) return Optional.empty();
            entries = new ArrayList<>();
            for(int idx = 0; idx < map.size(); idx++){
                Object entry = map.get(Integer.toString(idx));
                if(entry == null) return Optional.empty();
                entries.add(GraphValue.of(entry));
            }
        }else return Optional.empty();
        if(entries.isEmpty() || entries.size() > 256) return Optional.empty();
        List<Integer> colors = new ArrayList<>();
        for(var entry : entries){
            if(!"number".equals(entry.type()) || !Double.isFinite(entry.asNumber())) return Optional.empty();
            colors.add((int)Math.round(entry.asNumber()));
        }
        return Optional.of(new ColorRamp(colors));
    }

    // Read all resolved element paints as an immutable property map
    public static Map<String, ColorRamp> gradients(CompoundTag elm){
        Map<String, ColorRamp> paints = new java.util.LinkedHashMap<>();
        for(String property : PROPERTIES) gradient(elm, property).ifPresent(ramp -> paints.put(property, ramp));
        return Map.copyOf(paints);
    }

    // Replace stale gradient state whenever a binding returns to a solid color
    public static void bindGradient(CompoundTag elm, String property, GraphValue val){
        CompoundTag gradients = elm.getCompound(GRADIENTS);
        var ramp = gradient(val);
        if(ramp.isPresent()) gradients.putIntArray(property, ramp.get().colors());
        else gradients.remove(property);
        if(gradients.isEmpty()) elm.remove(GRADIENTS);
        else elm.put(GRADIENTS, gradients);
    }

    // Read resolved paint without retaining the mutable element tag
    public static Optional<ColorRamp> gradient(CompoundTag elm, String property){
        CompoundTag gradients = elm.getCompound(GRADIENTS);
        if(!gradients.contains(property, Tag.TAG_INT_ARRAY)) return Optional.empty();
        int[] colors = gradients.getIntArray(property);
        return colors.length == 0 || colors.length > 256 ? Optional.empty()
                : Optional.of(new ColorRamp(Arrays.stream(colors).boxed().toList()));
    }
}
