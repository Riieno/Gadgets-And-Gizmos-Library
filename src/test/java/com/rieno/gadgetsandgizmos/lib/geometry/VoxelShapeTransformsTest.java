package com.rieno.gadgetsandgizmos.lib.geometry;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoxelShapeTransformsTest{
    // A hinge must move the lens around its pivot while leaving empty block corners selectable as air
    @Test
    void hingeRotationMovesAnOffsetLensUpward(){
        var lens = Shapes.box(5.0 / 16, 5.0 / 16, 3.0 / 16, 11.0 / 16, 11.0 / 16, 5.0 / 16);
        var shape = VoxelShapeTransforms.rotate(lens,
                new Quaterniond().rotationAxis(Math.PI / 2, 1, 0, 0), new Vec3(0.5, 0.5, 0.5));
        assertNotNull(shape.clip(new Vec3(0.5, 2, 0.5), new Vec3(0.5, -1, 0.5), net.minecraft.core.BlockPos.ZERO));
        assertNull(shape.clip(new Vec3(0, 2, 0), new Vec3(0, -1, 0), net.minecraft.core.BlockPos.ZERO));
        assertEquals(13.0 / 16, shape.bounds().maxY, 1E-10);
        assertEquals(11.0 / 16, shape.bounds().minY, 1E-10);
    }

    // Arbitrary pan angles must enclose the visible geometry without filling the whole block
    @Test
    void rotatedStandRetainsItsNarrowBounds(){
        var stand = Shapes.box(3.0 / 16, 2.0 / 16, 6.5 / 16, 4.0 / 16, 10.0 / 16, 9.5 / 16);
        var shape = VoxelShapeTransforms.rotate(stand, new Quaterniond().rotationY(Math.PI / 4), new Vec3(0.5, 0.5, 0.5));
        assertTrue(shape.bounds().getXsize() < 0.25);
        assertTrue(shape.bounds().getZsize() < 0.25);
        assertEquals(0.5, shape.bounds().getYsize(), 1E-10);
        assertSame(Shapes.empty(), VoxelShapeTransforms.rotate(Shapes.empty(), new Quaterniond(), Vec3.ZERO));
    }
}
