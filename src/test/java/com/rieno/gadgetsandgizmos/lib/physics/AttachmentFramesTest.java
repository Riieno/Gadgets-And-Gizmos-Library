package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.core.Direction;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Cover every cardinal mounting frame, including reversed outward normals
class AttachmentFramesTest{
    @Test void unchangedFramesKeepTheirOrientation(){
        for(Direction normal : Direction.values()){
            for(Direction.Axis axis : Direction.Axis.values()){
                if(normal.getAxis() == axis) continue;
                var rotation = AttachmentFrames.alignAxes(normal, axis, normal, axis);
                assertEquals(1, Math.abs(rotation.w), 1E-12);
            }
        }
    }

    @Test void everyFrameMapsItsNormalAndUnsignedTangent(){
        for(Direction sourceNormal : Direction.values()){
            for(Direction.Axis sourceAxis : Direction.Axis.values()){
                if(sourceNormal.getAxis() == sourceAxis) continue;
                for(Direction targetNormal : Direction.values()){
                    for(Direction.Axis targetAxis : Direction.Axis.values()){
                        if(targetNormal.getAxis() == targetAxis) continue;
                        var rotation = AttachmentFrames.alignAxes(sourceNormal, sourceAxis, targetNormal, targetAxis);
                        assertVector(vector(targetNormal), rotation.transform(vector(sourceNormal)));
                        assertVector(positive(targetAxis), rotation.transform(positive(sourceAxis)));
                        var reverse = AttachmentFrames.alignAxes(targetNormal, targetAxis, sourceNormal, sourceAxis);
                        assertEquals(1, Math.abs(reverse.mul(rotation).w), 1E-12);
                    }
                }
            }
        }
    }

    @Test void invalidFramesAreRejected(){
        assertThrows(IllegalArgumentException.class, () -> AttachmentFrames.alignAxes(
                Direction.UP, Direction.Axis.Y, Direction.SOUTH, Direction.Axis.X));
        assertThrows(IllegalArgumentException.class, () -> AttachmentFrames.alignAxes(
                Direction.UP, Direction.Axis.X, Direction.SOUTH, Direction.Axis.Z));
        assertThrows(NullPointerException.class, () -> AttachmentFrames.alignAxes(
                null, Direction.Axis.X, Direction.SOUTH, Direction.Axis.Y));
    }

    private static Vector3d positive(Direction.Axis axis){
        return vector(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
    }

    private static Vector3d vector(Direction direction){
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static void assertVector(Vector3d expected, Vector3d actual){
        assertEquals(expected.x, actual.x, 1E-12);
        assertEquals(expected.y, actual.y, 1E-12);
        assertEquals(expected.z, actual.z, 1E-12);
    }
}
