package com.rieno.gadgetsandgizmos.lib.client.render;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

// Share the exact interpolated anchor with native projection calculations during one render call
public final class SubLevelProjectionContext{
    private static BlockEntity component;
    private static Pose3dc pose;

    private SubLevelProjectionContext(){}

    public static @Nullable Pose3dc pose(BlockEntity val){ return component == val ? pose : null; }
    public static @Nullable Pose3dc pose(){ return pose; }

    public static void render(BlockEntity val, @Nullable Pose3dc next, Runnable action){
        BlockEntity prev = component;
        Pose3dc prevPose = pose;
        component = val;
        pose = next;
        try{ action.run(); }
        finally{ component = prev; pose = prevPose; }
    }
}
