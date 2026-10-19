package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.core.Direction;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Objects;

// Align attachment frames whose tangent is an unsigned block axis
public final class AttachmentFrames{
    private AttachmentFrames(){}

    /**
     * Map a source outward normal and tangent axis onto a target attachment frame.
     * Tangents use the positive direction of their axis, independent of travel direction.
     * The normal and tangent axis in each frame must be perpendicular.
     *
     * @return a new normalized quaternion mapping source directions into the target frame
     */
    public static Quaterniond alignAxes(Direction sourceNormal, Direction.Axis sourceAxis,
            Direction targetNormal, Direction.Axis targetAxis){
        Quaterniond source = basis(sourceNormal, sourceAxis);
        Quaterniond target = basis(targetNormal, targetAxis);
        return target.mul(source.conjugate()).normalize();
    }

    private static Quaterniond basis(Direction normal, Direction.Axis axis){
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(axis, "axis");
        if(normal.getAxis() == axis) throw new IllegalArgumentException("Attachment normal and tangent must be perpendicular");
        Vector3d z = vector(normal);
        Vector3d x = vector(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
        Vector3d y = new Vector3d(z).cross(x);
        Matrix3d basis = new Matrix3d().setColumn(0, x).setColumn(1, y).setColumn(2, z);
        return new Quaterniond().setFromNormalized(basis).normalize();
    }

    private static Vector3d vector(Direction direction){
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }
}
