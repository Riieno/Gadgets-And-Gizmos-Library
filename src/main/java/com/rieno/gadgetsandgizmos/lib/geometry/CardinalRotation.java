package com.rieno.gadgetsandgizmos.lib.geometry;

import net.minecraft.core.Direction;
import org.joml.Quaterniond;
import org.joml.Vector3d;

// Describe an exact block-grid rotation between two attachment normals
public record CardinalRotation(Direction.Axis axis, int degrees){
    // Keep block placement and physics on the same exact quarter-turn grid
    public CardinalRotation{
        if(axis == null || degrees % 90 != 0) throw new IllegalArgumentException("A cardinal axis and quarter turns are required");
        degrees = Math.floorMod(degrees, 360);
    }

    // Select the same stable half-turn axis for physics and block placement
    public static CardinalRotation between(Direction from, Direction to){
        if(from == to) return new CardinalRotation(Direction.Axis.Y, 0);
        if(from == to.getOpposite()){
            return new CardinalRotation(from.getAxis() == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y, 180);
        }
        Vector3d cross = new Vector3d(from.getStepX(), from.getStepY(), from.getStepZ())
                .cross(to.getStepX(), to.getStepY(), to.getStepZ());
        if(cross.x != 0) return new CardinalRotation(Direction.Axis.X, cross.x > 0 ? 90 : -90);
        if(cross.y != 0) return new CardinalRotation(Direction.Axis.Y, cross.y > 0 ? 90 : -90);
        return new CardinalRotation(Direction.Axis.Z, cross.z > 0 ? 90 : -90);
    }

    // Rotate a local attachment frame with the same block-grid convention
    public Quaterniond quaternion(){
        return new Quaterniond().rotationAxis(Math.toRadians(degrees),
                axis == Direction.Axis.X ? 1 : 0, axis == Direction.Axis.Y ? 1 : 0,
                axis == Direction.Axis.Z ? 1 : 0);
    }
}
