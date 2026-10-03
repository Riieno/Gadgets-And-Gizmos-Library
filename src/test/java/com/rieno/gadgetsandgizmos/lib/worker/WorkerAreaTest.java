package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkerAreaTest{
    @Test void normalizesCornersAndUsesInclusiveBounds(){
        WorkerArea area = new WorkerArea(new BlockPos(5, 8, 9), new BlockPos(2, 3, 4));
        assertEquals(new BlockPos(2, 3, 4), area.min());
        assertEquals(new BlockPos(5, 8, 9), area.max());
        assertTrue(area.contains(new BlockPos(2, 3, 4)));
        assertTrue(area.contains(new BlockPos(5, 8, 9)));
        assertFalse(area.contains(new BlockPos(6, 8, 9)));
    }

    @Test void rejectsAreasBeyondTheScanBudget(){
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerArea(BlockPos.ZERO, new BlockPos(256, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerArea(BlockPos.ZERO, new BlockPos(63, 63, 31)));
    }

    @Test void movesOnlySelectedFaceAndRejectsCrossingOrOversize(){
        WorkerArea area = new WorkerArea(BlockPos.ZERO, new BlockPos(1, 1, 1));
        assertEquals(new BlockPos(-1, 0, 0), area.moveFace(Direction.WEST, -1).min());
        assertEquals(area.min(), area.moveFace(Direction.EAST, 1).min());
        assertNull(area.moveFace(Direction.WEST, 2));
        assertNull(area.moveFace(Direction.EAST, 255));
    }
}
