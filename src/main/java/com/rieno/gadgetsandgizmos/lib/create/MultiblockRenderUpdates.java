package com.rieno.gadgetsandgizmos.lib.create;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

// Refresh connected surfaces when a client receives or installs multiblock members
public final class MultiblockRenderUpdates{
    // Prevent instances of the shared notification helper
    private MultiblockRenderUpdates(){}

    // Include diagonal neighbours because their corner textures also depend on this member
    public static void refresh(BlockEntity entity){
        var level = entity.getLevel();
        if(level == null || !level.isClientSide) return;
        entity.requestModelDataUpdate();
        level.setBlocksDirty(entity.getBlockPos(), Blocks.AIR.defaultBlockState(), entity.getBlockState());
    }
}
