package com.rieno.gadgetsandgizmos.lib.color;

import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DyeColorRecipeTest{
    @Test
    void solidPaletteColoursCostOneMatchingDye(){
        for(var dye : DyeColor.values()){
            int[] expected = new int[16];
            expected[dye.getId()] = 1;
            assertArrayEquals(expected, DyeColorRecipe.recipe(solid(dye.getTextColor())), dye.getName());
        }
        int[] red = new int[16];
        red[DyeColor.RED.getId()] = 1;
        assertArrayEquals(red, DyeColorRecipe.additional(ColorEffectPreset.DEFAULT, solid(0xFF0000)));
    }

    @Test
    void gradientsAndCyclesUseOnlyTheirDistinctSelectedStopColours(){
        var stops = List.of(new ColorEffectPreset.Stop(0, 0xFF0000), new ColorEffectPreset.Stop(.4F, 0x0000FF),
                new ColorEffectPreset.Stop(1, 0xFF0000));
        var gradient = new ColorEffectPreset(ColorEffectPreset.Mode.GRADIENT, 0x00FF00, stops);
        int[] expected = new int[16];
        expected[DyeColor.RED.getId()] = expected[DyeColor.BLUE.getId()] = 1;
        assertArrayEquals(expected, DyeColorRecipe.recipe(gradient));
        assertArrayEquals(expected, DyeColorRecipe.recipe(gradient.withMode(ColorEffectPreset.Mode.CYCLE)));
        var uniform = new ColorEffectPreset(ColorEffectPreset.Mode.GRADIENT, 0x0000FF,
                List.of(new ColorEffectPreset.Stop(0, 0xFF0000), new ColorEffectPreset.Stop(1, 0xFF0000)));
        assertArrayEquals(DyeColorRecipe.recipe(solid(0xFF0000)), DyeColorRecipe.recipe(uniform));
    }

    @Test
    void customMixturesUseTheSmallestWholeIngredientRatio(){
        int[] equal = new int[16];
        equal[DyeColor.RED.getId()] = equal[DyeColor.WHITE.getId()] = 1;
        assertArrayEquals(equal, DyeColorRecipe.recipe(solid(0xFF8080)));
        int[] quarter = new int[16];
        quarter[DyeColor.RED.getId()] = 3;
        quarter[DyeColor.WHITE.getId()] = 1;
        assertArrayEquals(quarter, DyeColorRecipe.recipe(solid(0xFF4040)));
    }

    @Test
    void confirmingAgainOrRearrangingExistingColoursDoesNotChargeAgain(){
        var red = solid(0xFF0000);
        var gradient = ColorEffectPreset.fromColors(List.of(0xFF0000, 0x0000FF), false);
        int[] blue = new int[16];
        blue[DyeColor.BLUE.getId()] = 1;
        assertArrayEquals(blue, DyeColorRecipe.additional(red, gradient));
        assertArrayEquals(new int[16], DyeColorRecipe.additional(gradient, gradient));
        assertArrayEquals(new int[16], DyeColorRecipe.additional(gradient, gradient.withAnimated(false).withAdditive(true)));
        assertArrayEquals(new int[16], DyeColorRecipe.additional(gradient,
                ColorEffectPreset.fromColors(List.of(0x0000FF, 0xFF0000, 0x0000FF), false)));
        assertArrayEquals(new int[16], DyeColorRecipe.additional(gradient, ColorEffectPreset.DEFAULT));
    }

    @Test
    void additiveBlendingChargesGlowInkSeparatelyFromColourIngredients(){
        var red = solid(0xFF0000);
        var glowing = red.withAdditive(true);
        assertEquals(1, DyeColorRecipe.additionalGlowInkSacs(red, glowing));
        assertEquals(0, DyeColorRecipe.additionalGlowInkSacs(glowing, glowing));
        assertEquals(0, DyeColorRecipe.additionalGlowInkSacs(glowing, red));
        assertArrayEquals(DyeColorRecipe.recipe(red), DyeColorRecipe.recipe(glowing));
        assertArrayEquals(new int[16], DyeColorRecipe.recipe(ColorEffectPreset.DEFAULT));
    }

    private static ColorEffectPreset solid(int color){
        return ColorEffectPreset.DEFAULT.withMode(ColorEffectPreset.Mode.SOLID).withColor(color);
    }
}
