package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.function.BiFunction;

// Transform lenses through logical or interpolated SubLevel poses
public final class ViewTransforms{
    // Keep client interpolation behind a common-side pose provider
    private static BiFunction<SubLevel, Float, Pose3dc> clientPoses = (body, tick) -> body.logicalPose();
    // Prevent construction of the transform facade
    private ViewTransforms(){}
    // Install an interpolated pose provider during client setup
    public static void registerClientPoses(BiFunction<SubLevel, Float, Pose3dc> provider){
        clientPoses = java.util.Objects.requireNonNull(provider);
    }
    // Resolve one owning body pose without forcing an unloaded body to load
    private static Pose3dc pose(BlockEntity be, float partialTick){
        SubLevel body = SableLevelApi.containing(be);
        if(body == null) return null;
        return be.getLevel().isClientSide ? clientPoses.apply(body, partialTick) : body.logicalPose();
    }
    // Rotate a local mount into the current world frame
    public static Quaterniond orientation(BlockEntity be, Quaterniondc local, float partialTick){
        Pose3dc pose = pose(be, partialTick);
        return pose == null ? new Quaterniond(local) : new Quaterniond(pose.orientation()).mul(local);
    }
    // Project a local lens position through the current body pose
    public static Vec3 position(BlockEntity be, Vec3 local, float partialTick){
        Pose3dc pose = pose(be, partialTick);
        return pose == null ? local : pose.transformPosition(local);
    }
}
