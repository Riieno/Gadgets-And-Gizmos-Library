package com.rieno.gadgetsandgizmos.lib.display;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisplayTextScaleTest{
    @Test void missingAndInvalidSettingsKeepTheDefault(){
        assertEquals(1.0D, DisplayTextScale.read(null));
        CompoundTag tag = new CompoundTag();
        assertEquals(1.0D, DisplayTextScale.read(tag));
        for(double val : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}){
            tag.putDouble(DisplayTextScale.KEY, val);
            assertEquals(1.0D, DisplayTextScale.read(tag));
        }
        assertEquals(0.25D, DisplayTextScale.normalize(-10.0D));
        assertEquals(4.0D, DisplayTextScale.normalize(10.0D));
    }

    @Test void onlyLinkedTextAndSchedulesAcceptTheSetting(){
        CompoundTag frame = new CompoundTag();
        for(String state : new String[]{"display_link", "crn"}){
            frame.putString("State", state);
            assertTrue(DisplayTextScale.supportsFrame(frame));
            frame.putString("ContentNode", "authored_node");
            assertFalse(DisplayTextScale.supportsFrame(frame));
            frame.remove("ContentNode");
        }
        frame.putString("State", "external");
        CompoundTag external = new CompoundTag();
        frame.put("External", external);
        for(String format : new String[]{"text", "crn"}){
            external.putString("Format", format);
            assertTrue(DisplayTextScale.supportsFrame(frame));
        }
        for(String format : new String[]{"camera", "terminal", "computed", "diagnostic_tablet", ""}){
            external.putString("Format", format);
            assertFalse(DisplayTextScale.supportsFrame(frame));
        }
        frame.putString("State", "graph");
        assertFalse(DisplayTextScale.supportsFrame(frame));
    }

    @Test void largerTextReducesVisibleRowsWithoutOverlappingTheTaskbar(){
        DisplayTextScale.Layout normal = DisplayTextScale.layout(240, 120, 120, 10, 9, 1.0D);
        DisplayTextScale.Layout enlarged = DisplayTextScale.layout(240, 120, 120, 10, 9, 2.0D);
        DisplayTextScale.Layout reduced = DisplayTextScale.layout(240, 120, 120, 10, 9, 0.5D);
        assertTrue(enlarged.scale() > normal.scale());
        assertTrue(enlarged.visibleLines() < normal.visibleLines());
        assertTrue(enlarged.maximumLineWidth() < normal.maximumLineWidth());
        assertTrue(reduced.scale() < normal.scale());
        assertEquals(10, reduced.visibleLines());
        assertTrue(enlarged.top() + enlarged.lineHeight() * enlarged.visibleLines() <= 120.0D);
    }

    @Test void tinyAndCrowdedViewportsStayFiniteAndBounded(){
        for(int width : new int[]{1, 8, 64, 1024}){
            for(int height : new int[]{1, 8, 34, 1024}){
                for(double scale : new double[]{0.25D, 1.0D, 4.0D, Double.NaN}){
                    DisplayTextScale.Layout layout = DisplayTextScale.layout(width, height, 10000, 1000, 9, scale);
                    assertTrue(Double.isFinite(layout.scale()) && layout.scale() > 0.0D);
                    assertTrue(layout.top() >= 0.0D);
                    assertTrue(layout.top() + layout.lineHeight() * layout.visibleLines() <= height + 1.0E-8D);
                    assertTrue(layout.maximumLineWidth() * layout.scale() <= width + 1.0E-8D);
                }
            }
        }
    }

    @Test void scheduleCanvasFitsInsideThePhysicalDisplay(){
        for(int size : new int[]{16, 34, 64, 93, 4096}){
            for(double scale : new double[]{0.25D, 0.75D, 1.0D, 2.0D, 4.0D}){
                assertTrue(DisplayTextScale.canvasSize(size, scale) * scale <= size);
            }
        }
    }
}
