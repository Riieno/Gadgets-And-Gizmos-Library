package com.rieno.gadgetsandgizmos.lib.tablet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabletInventoryLayoutTest{
    @Test
    void stackedPlayerSlotsCannotOverlapCargoAndAllSlotsRemainOnTheCanvas(){
        var layout = TabletInventoryLayout.stacked(320, 40, 180, 17, 6);
        assertEquals(102, layout.storage().size());
        for(var grid : new TabletInventoryLayout.Grid[]{layout.storage(), layout.inventory(), layout.hotbar()}){
            for(int idx = 0; idx < grid.size(); idx++){
                assertTrue(grid.x(idx) >= 0 && grid.x(idx) + 16 <= 320);
                assertTrue(grid.y(idx) >= 0 && grid.y(idx) + 16 <= 270);
            }
        }
        assertTrue(layout.storage().y(101) + 16 < layout.inventory().top());
        assertTrue(layout.inventory().y(26) + 16 < layout.hotbar().top());
        assertThrows(IllegalArgumentException.class, () -> TabletInventoryLayout.stacked(100, 40, 180, 17, 6));
    }
}
