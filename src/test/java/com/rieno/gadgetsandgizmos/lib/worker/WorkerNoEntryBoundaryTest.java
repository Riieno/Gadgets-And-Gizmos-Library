package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkerNoEntryBoundaryTest{
    @Test void excludesWholeBodyAndTheSpaceAboveTheArea(){
        WorkerArea area = new WorkerArea(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2));
        assertTrue(WorkerNoEntryBoundary.blocks(new BlockPos(1, 80, 1), List.of(area)));
        assertFalse(WorkerNoEntryBoundary.blocks(new BlockPos(1, 63, 1), List.of(area)));
        assertTrue(WorkerNoEntryBoundary.intersects(new AABB(-0.2D, 70.0D, 0.2D,
                0.2D, 71.8D, 0.8D), List.of(area)));
        assertFalse(WorkerNoEntryBoundary.intersects(new AABB(-1.0D, 70.0D, 0.2D,
                -0.1D, 71.8D, 0.8D), List.of(area)));
    }

    @Test void ejectsAnEmbeddedWorkerSidewaysAcrossItsWholeBoundingBox(){
        WorkerArea area = new WorkerArea(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2));
        Vec3 position = new Vec3(1.5D, 67.0D, 1.5D);
        AABB body = new AABB(1.2D, 67.0D, 1.2D, 1.8D, 68.8D, 1.8D);
        Vec3 exit = WorkerNoEntryBoundary.nearestExit(position, body, List.of(area), candidate -> true);
        assertNotNull(exit);
        assertEquals(position.y, exit.y);
        assertFalse(WorkerNoEntryBoundary.intersects(body.move(exit.subtract(position)), List.of(area)));
    }

    @Test void blocksAJumpAcrossTheAreaEvenWhenTheLandingPointIsOutside(){
        WorkerArea area = new WorkerArea(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2));
        AABB body = new AABB(-0.8D, 70.0D, 0.2D, -0.2D, 71.8D, 0.8D);
        assertEquals(0.04D, WorkerNoEntryBoundary.firstBlockedFraction(body,
                new Vec3(5.0D, 0.0D, 0.0D), List.of(area)), 0.000001D);
        assertEquals(1.0D, WorkerNoEntryBoundary.firstBlockedFraction(body,
                new Vec3(5.0D, 0.0D, -30.0D), List.of(area)), 0.000001D);
    }
}
