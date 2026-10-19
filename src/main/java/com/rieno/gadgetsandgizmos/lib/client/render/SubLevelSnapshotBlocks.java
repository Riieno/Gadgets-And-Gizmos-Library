package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

// Decode bounded block-only sublevel previews for tablet and world renderers
public final class SubLevelSnapshotBlocks{
    private SubLevelSnapshotBlocks(){}

    public static List<SubLevelPreviewRenderer.SnapshotBlock> decode(ListTag rows,
                                                                       HolderLookup.Provider registries){
        List<SubLevelPreviewRenderer.SnapshotBlock> blocks = new ArrayList<>();
        if(rows == null || registries == null) return blocks;
        for(int idx = 0; idx < rows.size() && idx < 2048; idx++){
            var row = rows.getCompound(idx);
            if(!row.hasUUID("Body") || !row.contains("State")) continue;
            blocks.add(new SubLevelPreviewRenderer.SnapshotBlock(row.getUUID("Body"),
                    BlockPos.of(row.getLong("Pos")),
                    NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), row.getCompound("State")),
                    new Vec3(row.getDouble("X"), row.getDouble("Y"), row.getDouble("Z")),
                    row.contains("Qw") ? new Quaternionf(row.getFloat("Qx"), row.getFloat("Qy"),
                            row.getFloat("Qz"), row.getFloat("Qw")) : new Quaternionf()));
        }
        return List.copyOf(blocks);
    }
}
