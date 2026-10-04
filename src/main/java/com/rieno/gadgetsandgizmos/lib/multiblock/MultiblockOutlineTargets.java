package com.rieno.gadgetsandgizmos.lib.multiblock;

import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Resolve the blocks belonging to a selected multiblock
public final class MultiblockOutlineTargets {
    private static final int MAX_BLOCKS = 1024;

    private MultiblockOutlineTargets() {
    }

    // Let a block entity provide its exact outline positions
    public interface Source {
        Collection<BlockPos> outlineBlocks();
    }

    // Get the selected block and any connected members
    public static List<BlockPos> positions(Level level, BlockPos selected) {
        if (level == null || selected == null || !level.isLoaded(selected)) return List.of();
        BlockEntity blockEntity = level.getBlockEntity(selected);
        if (blockEntity instanceof Source source) {
            return source.outlineBlocks().stream().filter(pos -> pos != null && level.isLoaded(pos))
                    .distinct().limit(MAX_BLOCKS).toList();
        }
        if (!(blockEntity instanceof IMultiBlockEntityContainer multi) || multi.getController() == null) {
            return List.of(selected);
        }
        BlockPos controller = multi.getController();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        List<BlockPos> positions = new ArrayList<>();
        pending.add(selected);
        while (!pending.isEmpty() && positions.size() < MAX_BLOCKS) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos) || !level.isLoaded(pos)) continue;
            BlockEntity member = level.getBlockEntity(pos);
            if (!(member instanceof IMultiBlockEntityContainer joined)
                    || !controller.equals(joined.getController())) continue;
            positions.add(pos.immutable());
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = pos.relative(direction);
                if (!visited.contains(neighbour)) pending.add(neighbour);
            }
        }
        return List.copyOf(positions);
    }

    // Get the corners enclosing all supplied blocks
    public static Vec3[] outlineCorners(Collection<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) return new Vec3[0];
        int minX = blocks.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int minY = blocks.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int minZ = blocks.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxX = blocks.stream().mapToInt(BlockPos::getX).max().orElse(minX);
        int maxY = blocks.stream().mapToInt(BlockPos::getY).max().orElse(minY);
        int maxZ = blocks.stream().mapToInt(BlockPos::getZ).max().orElse(minZ);
        return new Vec3[] {
                new Vec3(minX - 0.01D, minY - 0.01D, minZ - 0.01D),
                new Vec3(maxX + 1.01D, minY - 0.01D, minZ - 0.01D),
                new Vec3(maxX + 1.01D, minY - 0.01D, maxZ + 1.01D),
                new Vec3(minX - 0.01D, minY - 0.01D, maxZ + 1.01D),
                new Vec3(minX - 0.01D, maxY + 1.01D, minZ - 0.01D),
                new Vec3(maxX + 1.01D, maxY + 1.01D, minZ - 0.01D),
                new Vec3(maxX + 1.01D, maxY + 1.01D, maxZ + 1.01D),
                new Vec3(minX - 0.01D, maxY + 1.01D, maxZ + 1.01D)
        };
    }
}
