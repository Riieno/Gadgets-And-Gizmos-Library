package com.rieno.gadgetsandgizmos.lib.client.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Keep projected controls in the same pointer regions across square and wide feeds
class ViewControlLayoutTest{
    @Test void squareFeedMatchesOverlayPointerLocations(){
        var layout = ViewControlLayout.overlay(0,0,640,640);
        assertTrue(layout.flashlight().contains(.043 * 640,.803 * 640));
        assertTrue(Math.hypot(.11 * 640 - layout.stickX(),.90 * 640 - layout.stickY()) < layout.radius());
        assertTrue(layout.modes().get(0).contains(.33 * 640,.974 * 640));
        assertTrue(layout.modes().get(1).contains(.50 * 640,.974 * 640));
        assertTrue(layout.modes().get(2).contains(.67 * 640,.974 * 640));
        assertTrue(layout.zoom().contains(.95 * 640,.90 * 640));
    }
    @Test void wideFeedKeepsZoomAndModesSeparateFromStick(){
        for(int height : new int[]{360,640}){
            var layout = ViewControlLayout.overlay(12,24,640,height);
            assertTrue(layout.stickX() - layout.radius() > 12);
            assertTrue(layout.stickY() + layout.radius() < 24 + height);
            for(var mode : layout.modes()){
                assertFalse(mode.contains(layout.stickX(),layout.stickY()));
                assertFalse(mode.contains(layout.zoom().centerX(),mode.centerY()));
                assertTrue(mode.y() + mode.height() < 24 + height);
            }
        }
    }
    @Test void compactLayoutRetainsTabletPointerLocations(){
        var layout = ViewControlLayout.compact(0,0,400,300);
        assertTrue(layout.modes().get(1).contains(200,236));
        assertTrue(layout.flashlight().contains(280,271));
        assertTrue(layout.zoom().contains(100,276));
    }
}
