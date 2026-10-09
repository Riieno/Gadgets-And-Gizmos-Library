package com.rieno.gadgetsandgizmos.lib.client.render;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Nested native renderer calls must not leak projection poses to other blocks or later frames
class SubLevelProjectionContextTest{
    @Test
    void nestedRenderersRestoreTheirOwnPoseAfterFailure(){
        var first = mock(BlockEntity.class);
        var second = mock(BlockEntity.class);
        var pose = new Pose3d();
        var nested = new Pose3d();
        assertNull(SubLevelProjectionContext.pose());
        SubLevelProjectionContext.render(first, pose, () -> {
            assertSame(pose, SubLevelProjectionContext.pose(first));
            assertNull(SubLevelProjectionContext.pose(second));
            assertThrows(IllegalStateException.class, () -> SubLevelProjectionContext.render(second, nested, () -> {
                assertSame(nested, SubLevelProjectionContext.pose(second));
                assertNull(SubLevelProjectionContext.pose(first));
                throw new IllegalStateException("Renderer failed");
            }));
            assertSame(pose, SubLevelProjectionContext.pose(first));
        });
        assertNull(SubLevelProjectionContext.pose());
    }
}
