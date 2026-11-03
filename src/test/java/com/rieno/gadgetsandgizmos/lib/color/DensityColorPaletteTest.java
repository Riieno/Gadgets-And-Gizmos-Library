package com.rieno.gadgetsandgizmos.lib.color;

import com.rieno.gadgetsandgizmos.lib.inventory.PaletteDyeStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DensityColorPaletteTest{
    @Test
    void denseColorsSettleLowerAndKeepTheirAmountBasedShare(){
        var palette = new DensityColorPalette(List.of(new DensityColorPalette.Layer(0xFF0000, 3, 0),
                new DensityColorPalette.Layer(0x0000FF, 1, 15)));
        var bands = palette.gradient().bands();
        assertEquals(0x0000FF, bands.getFirst().color());
        assertEquals(.25D, bands.getFirst().end());
        assertEquals(0xFF0000, bands.getLast().color());
        assertEquals(.45F, palette.mobility(0x0000FF), 1.0E-6F);
        assertEquals(1, palette.mobility(0xFF0000));
        assertEquals(0xBF003F, palette.mixedColor(0xFF0000));
        assertEquals(0xBF003F, palette.mixedColor(0x0000FF));
    }

    @Test
    void dyeDensityUsesTheOriginalIndexAndGlowInkAddsNoLayer(){
        var tag = new CompoundTag();
        int[] counts = new int[16];
        counts[DyeColor.WHITE.getId()] = 30;
        counts[DyeColor.RED.getId()] = 90;
        tag.putIntArray("Dyes", counts);
        tag.putInt("GlowInkSacs", 128);
        var storage = new PaletteDyeStorage();
        storage.read(tag);
        var palette = storage.densityPalette();
        assertEquals(2, palette.layers().size());
        assertEquals(14, palette.layers().getFirst().density());
        assertEquals(0, palette.layers().getLast().density());
        assertEquals(.75D, palette.gradient().bands().getFirst().end());
        assertEquals(DyeColor.WHITE.getTextureDiffuseColor() & 0xFFFFFF, storage.gradient().bands().getFirst().color());
        assertArrayEquals(counts, storage.write().getIntArray("Dyes"));
        assertEquals(128, storage.count(PaletteDyeStorage.GLOW_SLOT));
    }

    @Test
    void emptyAndInvalidLayersRemainBounded(){
        assertTrue(new DensityColorPalette(List.of(new DensityColorPalette.Layer(0, 0, 1))).layers().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new DensityColorPalette.Layer(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new DensityColorPalette.Layer(0, 1, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new DensityColorPalette(List.of(
                new DensityColorPalette.Layer(0, 1, 0), new DensityColorPalette.Layer(0, 1, 1))));
    }
}
