package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.color.DyeColorRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaletteDyeStorageTest{
    @Test
    void oneRedDyePaysForSolidRedWithoutOtherColoursOrAReserve(){
        int[] counts = new int[16];
        counts[DyeColor.RED.getId()] = 1;
        var storage = storage(counts, 0);
        var preset = ColorEffectPreset.DEFAULT.withMode(ColorEffectPreset.Mode.SOLID).withColor(0xFF0000);
        var cost = DyeColorRecipe.additional(ColorEffectPreset.DEFAULT, preset);
        assertTrue(storage.canPay(cost));
        assertTrue(PaletteDyeStorage.canPay(cost, 0, storage::count));
        assertTrue(storage.pay(cost));
        assertArrayEquals(new int[16], storage.write().getIntArray("Dyes"));
        assertTrue(storage.pay(DyeColorRecipe.additional(preset, preset)), "An unchanged preset needs no loaded dyes");
        assertFalse(storage.pay(cost), "The same dye cannot be spent twice");
    }

    @Test
    void glowAndDyesArePaidTogetherWithoutPartialDeductions(){
        int[] cost = new int[16];
        cost[DyeColor.RED.getId()] = 1;
        var missingGlow = storage(cost, 0);
        assertFalse(missingGlow.pay(cost, 1));
        assertArrayEquals(cost, missingGlow.write().getIntArray("Dyes"));
        var missingDye = storage(new int[16], 1);
        assertFalse(missingDye.pay(cost, 1));
        assertEquals(1, missingDye.count(PaletteDyeStorage.GLOW_SLOT));
        var exact = storage(cost, 1);
        assertTrue(exact.pay(cost, 1));
        assertArrayEquals(new int[16], exact.write().getIntArray("Dyes"));
        assertEquals(0, exact.count(PaletteDyeStorage.GLOW_SLOT));
    }

    @Test
    void synchronizedCountsUseTheSameEligibilityRulesAsStoredInventory(){
        int[] counts = new int[16];
        counts[DyeColor.RED.getId()] = counts[DyeColor.BLUE.getId()] = 1;
        var storage = storage(counts, 1);
        int[] view = new int[PaletteDyeStorage.SLOTS];
        System.arraycopy(counts, 0, view, 0, counts.length);
        view[PaletteDyeStorage.GLOW_SLOT] = 1;
        for(int glow : new int[]{0, 1, 2}){
            assertEquals(storage.canPay(counts, glow), PaletteDyeStorage.canPay(counts, glow, idx -> view[idx]));
        }
        view[DyeColor.BLUE.getId()] = 0;
        assertFalse(PaletteDyeStorage.canPay(counts, 1, idx -> view[idx]));
    }

    @Test
    void invalidAndOversizedCostsCannotConsumeOrCreateIngredients(){
        int[] counts = new int[16];
        counts[DyeColor.RED.getId()] = 128;
        var storage = storage(counts, 1);
        assertFalse(storage.pay(new int[15]));
        int[] invalid = new int[16];
        invalid[DyeColor.RED.getId()] = -1;
        assertFalse(storage.pay(invalid));
        invalid[DyeColor.RED.getId()] = Integer.MAX_VALUE;
        assertFalse(storage.pay(invalid));
        assertFalse(storage.pay(new int[16], -1));
        assertArrayEquals(counts, storage.write().getIntArray("Dyes"));
        assertEquals(1, storage.count(PaletteDyeStorage.GLOW_SLOT));
    }

    private static PaletteDyeStorage storage(int[] counts, int glow){
        var tag = new CompoundTag();
        tag.putIntArray("Dyes", counts);
        tag.putInt("GlowInkSacs", glow);
        var storage = new PaletteDyeStorage();
        storage.read(tag);
        return storage;
    }
}
