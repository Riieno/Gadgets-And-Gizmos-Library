package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

// Sample a saved gradient independently of its owning graph editor
public final class GradientGraphNode{
    public static final String PRESET = "Gradient";
    public static final int RESOLUTION = 100;

    private GradientGraphNode(){}

    // Retain both endpoints in exactly one hundred ordered samples
    public static Map<String, GraphValue> samples(ColorEffectPreset preset){
        Map<String, GraphValue> colors = new LinkedHashMap<>();
        for(int idx = 0; idx < RESOLUTION; idx++) colors.put(Integer.toString(idx),
                GraphValue.number(0xFF000000 | preset.samplePosition(idx / (float)(RESOLUTION - 1))));
        return colors;
    }

    // Clamp the percentage input while keeping the exported gradient independent of the selected position
    public static Map<String, GraphValue> execute(CompoundTag data, double value){
        var preset = preset(data);
        double position = Double.isFinite(value) ? Math.clamp(value, 0, 100) / 100 : 0;
        return Map.of("color", GraphValue.number(0xFF000000 | preset.samplePosition((float)position)), "gradient", GraphValue.map(samples(preset)));
    }

    // Keep node gradients static while reusing the same saved stops as color effect editors
    public static ColorEffectPreset preset(CompoundTag data){
        return ColorEffectPreset.read(data == null ? null : data.getCompound(PRESET))
                .withMode(ColorEffectPreset.Mode.GRADIENT).withAnimated(false);
    }
}
