package com.rieno.gadgetsandgizmos.lib.display;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectedSurfaceLayoutTest{
    @Test
    void singlePanelHasNoConnections(){
        assertEquals(0, new ConnectedSurfaceLayout(1, 1).cells(0, 0, 96, 96).getFirst().connections());
    }

    @Test
    void edgesAndCentreMatchTheConnectedSurface(){
        var cells = new ConnectedSurfaceLayout(3, 3).cells(0, 0, 288, 288);
        assertEquals(ConnectedSurfaceLayout.RIGHT | ConnectedSurfaceLayout.DOWN, cells.getFirst().connections());
        assertEquals(15, cells.get(4).connections());
        assertEquals(ConnectedSurfaceLayout.LEFT | ConnectedSurfaceLayout.UP, cells.getLast().connections());
    }

    @Test
    void fractionalTileSizesCoverTheSurfaceWithoutGaps(){
        var cells = new ConnectedSurfaceLayout(7, 3).cells(-31, 17, 101, 43);
        assertEquals(101 * 43, cells.stream().mapToInt(cell -> cell.width() * cell.height()).sum());
        for(int row = 0; row < 3; row++){
            for(int col = 1; col < 7; col++){
                var prev = cells.get(row * 7 + col - 1);
                assertEquals(prev.x() + prev.width(), cells.get(row * 7 + col).x());
            }
        }
    }

    @Test
    void oneRowUsesOnlyHorizontalConnections(){
        var cells = new ConnectedSurfaceLayout(3, 1).cells(0, 0, 288, 48);
        assertEquals(ConnectedSurfaceLayout.RIGHT, cells.getFirst().connections());
        assertEquals(ConnectedSurfaceLayout.LEFT | ConnectedSurfaceLayout.RIGHT, cells.get(1).connections());
        assertEquals(ConnectedSurfaceLayout.LEFT, cells.getLast().connections());
    }
}
