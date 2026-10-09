package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;

import java.util.Optional;

/** A signed, orthogonal craft frame in the owning body's local block coordinates. */
public record ScmOrientation(Direction forward, Direction up) {
    public ScmOrientation {
        if(!isValid(forward, up)){
            throw new IllegalArgumentException("Forward and up must use different axes");
        }
    }

    public static boolean isValid(Direction forward, Direction up){
        return forward != null && up != null && forward.getAxis() != up.getAxis();
    }

    /** Resolve a mounting frame without changing the sign of the requested forward direction. */
    public static ScmOrientation fromMount(Direction forward, Direction up){
        Direction resolvedForward = forward == null ? Direction.NORTH : forward;
        Direction resolvedUp = up == null ? Direction.UP : up;
        if(!isValid(resolvedForward, resolvedUp)){
            resolvedUp = resolvedForward.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        }
        return new ScmOrientation(resolvedForward, resolvedUp);
    }

    public Vec3 forwardVector(){
        return Vec3.atLowerCornerOf(forward.getNormal());
    }

    public Vec3 upVector(){
        return Vec3.atLowerCornerOf(up.getNormal());
    }

    public Vec3 rightVector(){
        return forwardVector().cross(upVector());
    }

    // Convert canonical north/up coordinates into the craft's body coordinates
    public Vec3 toBody(Vec3 val){
        return rightVector().scale(val.x).add(upVector().scale(val.y))
                .subtract(forwardVector().scale(val.z));
    }

    // Convert physical body coordinates into canonical north/up coordinates
    public Vec3 fromBody(Vec3 val){
        return new Vec3(val.dot(rightVector()), val.dot(upVector()), -val.dot(forwardVector()));
    }

    // Rotate canonical north/up axes into this signed craft frame
    public Quaterniond rotation(){
        Vec3 right = rightVector();
        Vec3 vertical = upVector();
        Vec3 back = forwardVector().scale(-1);
        return new Matrix3d(right.x, right.y, right.z, vertical.x, vertical.y, vertical.z,
                back.x, back.y, back.z).getNormalizedRotation(new Quaterniond());
    }

    // Express a physical body tensor in the canonical control axes
    public Matrix3d fromBodyTensor(Matrix3dc val){
        Matrix3d rotation = new Matrix3d().rotation(rotation());
        return new Matrix3d(rotation).transpose().mul(val).mul(rotation);
    }

    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.putString("Forward", forward.getName());
        tag.putString("Up", up.getName());
        return tag;
    }

    /** Missing, unknown and parallel directions are unavailable, never an invented custom frame. */
    public static Optional<ScmOrientation> fromTag(CompoundTag tag){
        if(tag == null) return Optional.empty();
        Direction forward = Direction.byName(tag.getString("Forward"));
        Direction up = Direction.byName(tag.getString("Up"));
        return isValid(forward, up) ? Optional.of(new ScmOrientation(forward, up)) : Optional.empty();
    }
}
