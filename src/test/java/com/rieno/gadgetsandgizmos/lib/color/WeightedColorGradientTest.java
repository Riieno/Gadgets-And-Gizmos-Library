package com.rieno.gadgetsandgizmos.lib.color;

import com.rieno.gadgetsandgizmos.lib.inventory.PaletteDyeStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeightedColorGradientTest{
    @Test
    void unequalAmountsOccupyTheirShareAndBlendAcrossTheBoundary(){
        var gradient = new WeightedColorGradient(List.of(new WeightedColorGradient.Color(0xFF0000, 96),
                new WeightedColorGradient.Color(0x00FF00, 0), new WeightedColorGradient.Color(0x0000FF, 32)));
        assertEquals(List.of(new WeightedColorGradient.Band(0, .75D, 0xFF0000),
                new WeightedColorGradient.Band(.75D, 1, 0x0000FF)), gradient.bands());
        assertEquals(0xFF0000, gradient.sample(.375D));
        assertEquals(0x800080, gradient.sample(.75D));
        assertEquals(0x0000FF, gradient.sample(.875D));
        assertEquals(0xFF0000, gradient.sample(-1));
        assertEquals(0x0000FF, gradient.sample(2));
    }

    @Test
    void emptySingleColorAndLargeAmountsRemainWellDefined(){
        var empty = new WeightedColorGradient(List.of());
        assertTrue(empty.bands().isEmpty());
        assertEquals(0, empty.sample(.5D));
        var colors = new ArrayList<WeightedColorGradient.Color>();
        colors.add(new WeightedColorGradient.Color(0xFF123456, 1));
        var single = new WeightedColorGradient(colors);
        colors.clear();
        assertEquals(0x123456, single.sample(0));
        assertEquals(0x123456, single.sample(.5D));
        assertEquals(0x123456, single.sample(1));
        assertThrows(IllegalArgumentException.class, () -> new WeightedColorGradient.Color(0, -1));
        var large = new WeightedColorGradient(List.of(new WeightedColorGradient.Color(1, Integer.MAX_VALUE),
                new WeightedColorGradient.Color(2, Integer.MAX_VALUE)));
        assertEquals(.5D, large.bands().getFirst().end());
    }

    @Test
    void paletteUsesActualDyeCountsAndExcludesGlowInk(){
        var tag = new CompoundTag();
        int[] dyes = new int[16];
        dyes[DyeColor.RED.getId()] = 96;
        dyes[DyeColor.BLUE.getId()] = 32;
        tag.putIntArray("Dyes", dyes);
        tag.putInt("GlowInkSacs", 128);
        var storage = new PaletteDyeStorage();
        storage.read(tag);
        var bands = storage.gradient().bands();
        assertEquals(2, bands.size());
        assertEquals(DyeColor.BLUE.getTextureDiffuseColor() & 0xFFFFFF, bands.getFirst().color());
        assertEquals(.25D, bands.getFirst().end());
        assertEquals(DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF, bands.getLast().color());
        assertEquals(.75D, bands.getLast().end() - bands.getLast().start());
        assertArrayEquals(dyes, storage.write().getIntArray("Dyes"));
        tag.putIntArray("Dyes", new int[16]);
        storage.read(tag);
        assertTrue(storage.gradient().bands().isEmpty());
    }
}
