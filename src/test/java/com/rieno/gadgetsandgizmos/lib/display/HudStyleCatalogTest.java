package com.rieno.gadgetsandgizmos.lib.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudStyleCatalogTest{
    @Test void eachInteractiveControlOffersOnlyTheSixNewLooks(){
        for(String type : new String[]{"button", "toggle", "slider"}){
            assertEquals(6, HudStyleCatalog.options(type).size());
            assertEquals(2, HudStyleCatalog.options(type).stream().filter(o -> o.id().startsWith("material_")).count());
            assertEquals(2, HudStyleCatalog.options(type).stream().filter(o -> o.id().startsWith("neumorphic_")).count());
            assertEquals(2, HudStyleCatalog.options(type).stream().filter(o -> o.id().startsWith("skeuomorphic_")).count());
            assertTrue(HudStyleCatalog.options(type).stream().noneMatch(o -> o.id().equals("create_painted")));
        }
        assertEquals(14, HudStyleCatalog.options("progress").size());
        assertTrue(HudStyleCatalog.options("text").isEmpty());
    }

    @Test void existingDesignsMapToNewStylesWithoutKeepingLegacyPickerEntries(){
        assertEquals("skeuomorphic_beveled", HudStyleCatalog.style("slider", "tnt_brass"));
        assertEquals("material_outlined", HudStyleCatalog.style("button", "controller_graph"));
        assertEquals("material_filled", HudStyleCatalog.style("toggle", "unknown"));
        assertEquals("solid", HudStyleCatalog.style("progress", ""));
        assertTrue(HudStyleCatalog.checkbox(" Checkbox "));
        assertFalse(HudStyleCatalog.checkbox(null));
    }

    @Test void rangeNormalizationHandlesEdgesAndUnavailableValues(){
        assertEquals(0.5, HudStyleCatalog.amount(15, 10, 20));
        assertEquals(0, HudStyleCatalog.amount(-1, 0, 100));
        assertEquals(1, HudStyleCatalog.amount(101, 0, 100));
        assertEquals(0, HudStyleCatalog.amount(Double.NaN, 0, 1));
        assertEquals(0, HudStyleCatalog.amount(Double.POSITIVE_INFINITY, 0, 1));
        assertEquals(0.5, HudStyleCatalog.amount(0.5, 0, 0));
    }
}
