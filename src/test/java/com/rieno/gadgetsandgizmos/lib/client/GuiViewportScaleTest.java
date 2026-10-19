package com.rieno.gadgetsandgizmos.lib.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiViewportScaleTest{
    @Test void smallWindowsLeaveRoomForControls(){
        assertEquals(1, GuiViewportScale.forWidth(2, 900, 800));
        assertEquals(1, GuiViewportScale.forWidth(2, 600, 800));
    }
    @Test void normalWindowsKeepTheirPreferredScale(){
        assertEquals(2, GuiViewportScale.forWidth(2, 1920, 800));
        assertEquals(3, GuiViewportScale.forWidth(4, 2560, 800));
        assertEquals(4, GuiViewportScale.forWidth(4, 900, 0));
    }
    @Test void invalidDimensionsCannotProduceAnInvalidScale(){
        assertEquals(1, GuiViewportScale.forWidth(0, 0, 800));
        assertEquals(1, GuiViewportScale.forWidth(-1, -100, -2));
    }
}
