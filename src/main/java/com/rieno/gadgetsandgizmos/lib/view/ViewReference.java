package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Identify a loaded view source in a dimension and optional SubLevel
public record ViewReference(ResourceLocation dimension, @Nullable UUID subLevelId, BlockPos blockPos){
    // Retain an immutable source position
    public ViewReference{ blockPos = blockPos.immutable(); }

    // Resolve only a loaded source in the requested dimension
    public @Nullable ViewSource resolve(Level level){
        if(level == null || !dimension.equals(level.dimension().location())
                || !SubLevelBlockEntityCollector.isTargetLoaded(level, subLevelId, blockPos)) return null;
        Level target = SubLevelBlockEntityCollector.resolveTargetLevel(level, subLevelId);
        if(target == null) return null;
        BlockEntity be = target.getBlockEntity(blockPos);
        return be instanceof ViewSource src && src.isViewAvailable() ? src : null;
    }

    // Capture the current source ownership
    public static ViewReference of(BlockEntity be){
        return new ViewReference(be.getLevel().dimension().location(),
                SableLevelApi.containingId(be), be.getBlockPos());
    }

    // Save a source reference for packets and display frames
    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", dimension.toString());
        tag.putLong("BlockPos", blockPos.asLong());
        if(subLevelId != null) tag.putUUID("SubLevel", subLevelId);
        return tag;
    }

    // Read a source reference without inventing a default target
    public static @Nullable ViewReference fromTag(CompoundTag tag){
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dimension"));
        if(dim == null || !tag.contains("BlockPos")) return null;
        return new ViewReference(dim, tag.hasUUID("SubLevel") ? tag.getUUID("SubLevel") : null,
                BlockPos.of(tag.getLong("BlockPos")));
    }
}
