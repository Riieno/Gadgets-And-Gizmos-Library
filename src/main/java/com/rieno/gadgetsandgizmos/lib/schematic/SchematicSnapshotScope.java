package com.rieno.gadgetsandgizmos.lib.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

// Substitute server-approved block data only during one synchronous schematic capture
public final class SchematicSnapshotScope implements AutoCloseable{
    private static final ThreadLocal<Map<BlockPos, CompoundTag>> CURRENT = new ThreadLocal<>();
    private final Map<BlockPos, CompoundTag> prev;

    private SchematicSnapshotScope(Map<BlockPos, CompoundTag> snapshots){
        prev = CURRENT.get();
        CURRENT.set(Map.copyOf(snapshots));
    }

    // Borrow immutable snapshot values until the capture finishes on this thread
    public static SchematicSnapshotScope enter(Map<BlockPos, CompoundTag> snapshots){
        return new SchematicSnapshotScope(snapshots);
    }

    // Return an independent tag for the block currently being captured
    public static @Nullable CompoundTag data(BlockEntity blockEntity){
        Map<BlockPos, CompoundTag> snapshots = CURRENT.get();
        CompoundTag data = snapshots == null ? null : snapshots.get(blockEntity.getBlockPos());
        if(data == null || !(blockEntity instanceof SchematicSnapshotProvider)) return null;
        CompoundTag res = data.copy();
        BlockEntity.addEntityType(res, blockEntity.getType());
        return res;
    }

    // Restore an enclosing capture even when the exporter throws
    @Override
    public void close(){
        if(prev == null) CURRENT.remove();
        else CURRENT.set(prev);
    }
}
