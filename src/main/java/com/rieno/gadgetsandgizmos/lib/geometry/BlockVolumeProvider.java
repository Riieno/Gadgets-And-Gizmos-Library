package com.rieno.gadgetsandgizmos.lib.geometry;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.probe.LoadedBlockAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

// Let several block entities expose the same owner-defined selection and collision volume
public interface BlockVolumeProvider{
    // Return the current shared volume or null when its owner is unavailable
    @Nullable BlockVolume getBlockVolume();

    // Resolve a loaded block entity without retaining its level or ownership
    static @Nullable BlockVolume find(BlockGetter level, BlockPos pos){
        var be = level instanceof Level world
                ? LoadedBlockAccess.blockEntity(world, SableLevelApi.containingId(world, pos), pos)
                : level.getBlockEntity(pos);
        return be instanceof BlockVolumeProvider provider && !be.isRemoved() ? provider.getBlockVolume() : null;
    }
}
