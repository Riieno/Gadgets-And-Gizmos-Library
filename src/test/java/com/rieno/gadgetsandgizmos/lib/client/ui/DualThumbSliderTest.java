package com.rieno.gadgetsandgizmos.lib.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verify endpoint ordering, coincident thumbs and adjusted pointer movement
class DualThumbSliderTest{
    @Test
    void endpointsClampWithoutCrossing(){
        var slider = new DualThumbSlider(-90, 90, -45, 45);
        assertTrue(slider.setLower(70));
        assertEquals(45, slider.lower());
        assertFalse(slider.setUpper(-70));
        assertEquals(45, slider.upper());
        slider.setRange(200, -200);
        assertEquals(-90, slider.lower());
        assertEquals(90, slider.upper());
        assertEquals(0, slider.lowerFraction());
        assertEquals(1, slider.upperFraction());
    }

    @Test
    void grabbingAThumbPreservesItsPointerOffset(){
        var slider = new DualThumbSlider(-90, 90, -45, 45);
        assertFalse(slider.beginDrag(0.27, false));
        assertEquals(DualThumbSlider.Thumb.LOWER, slider.selectedThumb());
        assertFalse(slider.dragTo(0.27, 1, 0));
        assertEquals(-45, slider.lower());
        assertTrue(slider.dragTo(0.37, 1, 0));
        assertEquals(-27, slider.lower(), 1.0E-8);
        assertEquals(45, slider.upper());
        assertTrue(slider.endDrag());
        assertFalse(slider.dragTo(0, 1, 0));
    }

    @Test
    void coincidentThumbsCanBothMoveAgain(){
        var slider = new DualThumbSlider(-90, 90, 0, 0);
        slider.beginDrag(0.5, false);
        assertEquals(DualThumbSlider.Thumb.LOWER, slider.selectedThumb());
        assertTrue(slider.dragTo(0.25, 1, 0));
        assertEquals(-45, slider.lower());
        slider.endDrag();
        slider.setLower(0);
        slider.beginDrag(0.5, false);
        assertEquals(DualThumbSlider.Thumb.UPPER, slider.selectedThumb());
        assertTrue(slider.dragTo(0.75, 1, 0));
        assertEquals(45, slider.upper());
    }

    @Test
    void fineMovementAndSnappingUseTheOriginalDragValue(){
        var slider = new DualThumbSlider(-90, 90, -45, 45);
        slider.beginDrag(0.25, false);
        slider.dragTo(0.45, 0.25, 0);
        assertEquals(-36, slider.lower(), 1.0E-8);
        slider.dragTo(0.45, 1, 5);
        assertEquals(-10, slider.lower());
        slider.dragTo(0, 1, 0);
        assertEquals(-90, slider.lower());
        slider.endDrag();
        slider.beginDrag(0.75, false);
        slider.dragTo(1, 1, 0);
        assertEquals(90, slider.upper());
    }

    @Test
    void clickingTheRailChoosesTheNearestEndpoint(){
        var slider = new DualThumbSlider(-90, 90, -45, 45);
        assertTrue(slider.beginDrag(0.1, true));
        assertEquals(-72, slider.lower(), 1.0E-8);
        assertTrue(slider.dragTo(0.15, 1, 0));
        assertEquals(-63, slider.lower(), 1.0E-8);
        slider.endDrag();
        assertTrue(slider.beginDrag(0.9, true));
        assertEquals(DualThumbSlider.Thumb.UPPER, slider.selectedThumb());
        assertEquals(72, slider.upper(), 1.0E-8);
    }

    @Test
    void invalidValuesCannotEnterTheRange(){
        assertThrows(IllegalArgumentException.class, () -> new DualThumbSlider(1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new DualThumbSlider(0, 1, Double.NaN, 1));
        var slider = new DualThumbSlider(-90, 90, -45, 45);
        assertThrows(IllegalArgumentException.class, () -> slider.setUpper(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> slider.beginDrag(Double.NaN, false));
        assertThrows(IllegalArgumentException.class, () -> slider.dragTo(0.5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> slider.dragTo(0.5, 1, -1));
        assertEquals(-45, slider.lower());
        assertEquals(45, slider.upper());
    }
}
