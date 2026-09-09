package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

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
