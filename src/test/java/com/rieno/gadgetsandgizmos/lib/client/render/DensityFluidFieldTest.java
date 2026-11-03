package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.color.DensityColorPalette;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DensityFluidFieldTest{
    private static final DensityColorPalette PALETTE = new DensityColorPalette(List.of(
            new DensityColorPalette.Layer(0xFF0000, 1, 0), new DensityColorPalette.Layer(0x0000FF, 1, 15)));

    @Test
    void surfaceSamplingKeepsDistinctColourCoresWithContinuousNarrowEdges(){
        var palette = new DensityColorPalette(List.of(new DensityColorPalette.Layer(0xEBC641, 1, 4),
                new DensityColorPalette.Layer(0x3456BD, 1, 11), new DensityColorPalette.Layer(0xC34735, 1, 14)));
        var field = new DensityFluidField(new AABB(0, 0, 0, 1, 1, 1), .85F, palette);
        field.update(new DensityFluidMotion.Frame(0, 1, 0, 0, 0, 0), 0);
        for(int tick = 1; tick <= 60; tick++) field.update(new DensityFluidMotion.Frame(0, 1, 0, .9F, -.7F, .9F), tick);
        int cores = 0, edges = 0;
        var seen = new java.util.HashSet<Integer>();
        for(int y = 1; y < 16; y++) for(int x = 1; x < 30; x++){
            double px = x / 30D, py = y / 20D;
            int color = field.sampleSurface(px, py, .45D);
            boolean core = palette.layers().stream().anyMatch(layer -> layer.color() == color);
            if(core){ cores++; seen.add(color); }else edges++;
            int nearby = field.sampleSurface(px + .000001D, py, .45D);
            for(int shift : new int[]{0, 8, 16}) assertTrue(Math.abs((color >> shift & 255) - (nearby >> shift & 255)) <= 2);
        }
        assertEquals(3, seen.size());
        assertTrue(cores > edges * 3, "Most moving fluid must retain an original dye colour");
        assertTrue(edges > 0, "Colour boundaries must have a narrow smooth halo");
    }

    @Test
    void agitatedPaletteSamplingRetainsOriginalDyesWithoutCreatingAveragedColours(){
        var palette = new DensityColorPalette(List.of(new DensityColorPalette.Layer(0xEBC641, 1, 4),
                new DensityColorPalette.Layer(0x3456BD, 1, 11), new DensityColorPalette.Layer(0xC34735, 1, 14)));
        var field = new DensityFluidField(new AABB(0, 0, 0, 1, 1, 1), .85F, palette);
        field.update(new DensityFluidMotion.Frame(0, 1, 0, 0, 0, 0), 0);
        for(int tick = 1; tick <= 80; tick++) field.update(new DensityFluidMotion.Frame(0, 1, 0, .9F, .8F, .95F), tick);
        var seen = new java.util.HashSet<Integer>();
        for(int y = 1; y < 8; y++) for(int x = 1; x < 10; x++) for(int z = 1; z < 10; z++){
            int color = field.samplePalette(x / 10D, y / 10D, z / 10D);
            assertTrue(palette.layers().stream().anyMatch(layer -> layer.color() == color));
            seen.add(color);
        }
        assertEquals(3, seen.size(), "Agitation must keep all source colours visible");
        for(int tick = 81; tick <= 120; tick++) field.update(new DensityFluidMotion.Frame(0, 1, 0, 0, 0, 0), tick);
        assertTrue(field.settled(), "Density layers must reform within two seconds after flow stops");
    }

    @Test
    void dyesCrossTheirOriginalBoundaryThenSeparateWithoutLosingTheirAmounts(){
        var field = new DensityFluidField(new AABB(0, 0, 0, 1, 1, 1), .85F, PALETTE);
        var rest = new DensityFluidMotion.Frame(0, 1, 0, 0, 0, 0);
        field.update(rest, 0);
        int before = field.sample(.2D, .25D, .5D, 1);
        assertTrue((before & 255) > 220 && (before >> 16 & 255) < 30);
        for(int tick = 1; tick <= 55; tick++){
            field.update(new DensityFluidMotion.Frame(0, 1, 0, .9F, .65F, .9F), tick);
        }
        int mixed = field.sample(.2D, .25D, .5D, 1);
        assertTrue((mixed >> 16 & 255) > 45, "Red dye must be carried into a region that originally contained blue");
        assertTrue((mixed & 255) > 30, "The region must contain both dyes");
        assertEquals(.5D, field.fraction(0xFF0000), .015D);
        assertEquals(.5D, field.fraction(0x0000FF), .015D);
        for(int tick = 56; tick <= 500; tick++) field.update(rest, tick);
        assertTrue(field.settled());
        assertEquals(before, field.sample(.2D, .25D, .5D, 1));
        long steps = field.steps();
        for(int tick = 501; tick <= 800; tick++) assertFalse(field.update(rest, tick));
        assertEquals(steps, field.steps());
    }

    @Test
    void dyeFieldIsBoundedAcrossTiltAndTimeResets(){
        var field = new DensityFluidField(new AABB(0, 0, 0, .175D, .175D, .425D), .85F, PALETTE);
        field.update(new DensityFluidMotion.Frame(0, 1, 0, 0, 0, 0), 0);
        for(int tick = 1; tick <= 80; tick++){
            var up = new org.joml.Vector3f((float)Math.sin(tick * .03D), .7F, .3F).normalize();
            field.update(new DensityFluidMotion.Frame(up.x, up.y, up.z, .8F, -.7F, .9F), tick);
            int color = field.sample(.175D, .1D, .425D, .5F);
            assertEquals(0, color & 0xFF000000);
            assertEquals(.5D, field.fraction(0xFF0000), .025D);
        }
        assertTrue(field.update(new DensityFluidMotion.Frame(0, -1, 0, 0, 0, 0), 1000));
        assertTrue(field.settled());
    }
}
