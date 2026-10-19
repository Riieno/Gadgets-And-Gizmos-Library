package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Check that rotated child models retain their block centers in a parent preview
class SubLevelPreviewGeometryTest{
    // Keep a quarter-turn head centered on its transformed block
    @Test void quarterTurnUsesTheBlockCenter(){
        Pose3d child = new Pose3d();
        child.position().set(10, 0, 0);
        child.orientation().rotateY(Math.PI / 2);
        Vec3 origin = SableTransformApi.relativeBlockOrigin(child, new Pose3d(), BlockPos.ZERO, BlockPos.ZERO);
        assertEquals(10, origin.x, 1E-9);
        assertEquals(0, origin.y, 1E-9);
        assertEquals(-1, origin.z, 1E-9);
    }

    // Preserve a child's local placement when its parent rotates far from the plot origin
    @Test void nestedBodiesRetainTheirRelativePlacement(){
        BlockPos rootOrigin = new BlockPos(1_000_000, 0, -1_000_000);
        BlockPos childOrigin = new BlockPos(-2_000_000, 0, 2_000_000);
        Pose3d root = new Pose3d();
        root.rotationPoint().set(rootOrigin.getX(), rootOrigin.getY(), rootOrigin.getZ());
        root.position().set(100, 20, -50);
        root.orientation().rotateY(Math.PI / 2);
        Pose3d child = new Pose3d();
        child.rotationPoint().set(childOrigin.getX(), childOrigin.getY(), childOrigin.getZ());
        child.orientation().set(root.orientation());
        child.position().set(root.transformPosition(new Vector3d(rootOrigin.getX() + 4, 2, rootOrigin.getZ() + 6)));
        Vec3 origin = SableTransformApi.relativeBlockOrigin(child, root, rootOrigin, childOrigin);
        assertEquals(4, origin.x, 1E-8);
        assertEquals(2, origin.y, 1E-8);
        assertEquals(6, origin.z, 1E-8);
    }
}
