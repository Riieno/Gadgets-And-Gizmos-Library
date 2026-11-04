package com.rieno.gadgetsandgizmos.lib.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultiblockPanelLayoutTest{
    @Test
    void identicalGeometrySharesImmutablePanelsAndNormalizesDimensions(){
        var pos = new BlockPos(10, 20, 30);
        var panels = MultiblockPanelLayout.panels(pos, 0, -1, 1, Direction.Axis.Y, true, true);
        assertSame(panels, MultiblockPanelLayout.panels(pos, 1, 1, 1, Direction.Axis.Y, true, true));
        assertEquals(4, panels.size());
        assertThrows(UnsupportedOperationException.class, () -> panels.clear());
        for(var panel : panels) assertTrue(panel.contains(panel.center(), panel.facing(), .75, .6));
    }

    @Test
    void changedDimensionsAndMutableOriginsDoNotReuseStalePanels(){
        var pos = new BlockPos.MutableBlockPos(1, 2, 3);
        var first = MultiblockPanelLayout.panels(pos, 2, 3, 4, Direction.Axis.X, false, false);
        assertEquals(2, first.size());
        pos.set(5, 6, 7);
        var moved = MultiblockPanelLayout.panels(pos, 2, 3, 4, Direction.Axis.X, false, false);
        assertNotEquals(first, moved);
        assertSame(first, MultiblockPanelLayout.panels(new BlockPos(1, 2, 3), 2, 3, 4, Direction.Axis.X, false, false));
        var resized = MultiblockPanelLayout.panels(new BlockPos(1, 2, 3), 3, 3, 4, Direction.Axis.X, false, false);
        assertNotEquals(first, resized);
        assertTrue(first.stream().allMatch(panel -> panel.facing().getAxis() == Direction.Axis.X));
    }
}
