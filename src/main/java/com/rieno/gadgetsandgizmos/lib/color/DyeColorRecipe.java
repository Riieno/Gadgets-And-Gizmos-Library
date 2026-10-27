package com.rieno.gadgetsandgizmos.lib.color;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

// Quantize colors into four dye units, charging only positive changes from the current preset
public final class DyeColorRecipe{
    private static final int UNITS = 4;
    private DyeColorRecipe(){}

    // Resolve vanilla dyes and modded ingredients in any of the sixteen common color tags
    @Nullable
    public static DyeColor colorOf(ItemStack stack){
        if(stack.isEmpty()) return null;
        if(stack.getItem() instanceof DyeItem dye) return dye.getDyeColor();
        for(var color : DyeColor.values()) if(stack.is(color.getTag())) return color;
        return null;
    }

    public static int[] recipe(ColorEffectPreset preset){
        var result = new int[16];
        if(!preset.active()) return result;
        if(preset.mode() == ColorEffectPreset.Mode.SOLID) addColor(result, preset.color());
        else for(var stop : preset.stops()) addColor(result, stop.color());
        return result;
    }

    public static int[] additional(ColorEffectPreset current, ColorEffectPreset next){
        var previous = recipe(current);
        var result = recipe(next);
        for(int idx = 0; idx < result.length; idx++) result[idx] = Math.max(0, result[idx] - previous[idx]);
        return result;
    }

    // Charge one glow ink sac only when enabling additive blending
    public static int additionalGlowInkSacs(ColorEffectPreset current, ColorEffectPreset next){
        return next.additive() && !current.additive() ? 1 : 0;
    }

    // Find the closest mixture of two vanilla dye colors at one-quarter increments
    private static void addColor(int[] result, int rgb){
        long best = Long.MAX_VALUE;
        int first = 0, second = 0, amount = 0;
        for(DyeColor a : DyeColor.values()){
            for(DyeColor b : DyeColor.values()){
                for(int units = 0; units <= UNITS; units++){
                    int mixed = ColorGradient.blend(a.getTextColor(), b.getTextColor(), units / (float)UNITS);
                    long distance = 0;
                    for(int shift : new int[]{0, 8, 16}){
                        int delta = (mixed >> shift & 255) - (rgb >> shift & 255);
                        distance += (long)delta * delta;
                    }
                    if(distance >= best) continue;
                    best = distance;
                    first = a.getId(); second = b.getId(); amount = units;
                }
            }
        }
        result[first] += UNITS - amount;
        result[second] += amount;
    }
}
