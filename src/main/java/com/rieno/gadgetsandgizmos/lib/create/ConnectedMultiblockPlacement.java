package com.rieno.gadgetsandgizmos.lib.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

// Build reusable placement planes for Create-style connected storage
public final class ConnectedMultiblockPlacement {
    // Initialize the placement helper
    private ConnectedMultiblockPlacement() {
    }

    // Get the next square vault cross-section at either end of its main axis
    public static List<BlockPos> vaultExtension(BlockPos controller, Direction.Axis axis,
                                                int width, int length, Direction placedFace) {
        if (controller == null || axis == null || placedFace == null
                || placedFace.getAxis() != axis || width <= 1 || length <= 0) return List.of();
        Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        BlockPos origin = placedFace == positive.getOpposite()
                ? controller.relative(positive.getOpposite()) : controller.relative(positive, length);
        List<BlockPos> positions = new ArrayList<>(width * width);
        for (int first = 0; first < width; first++) {
            for (int second = 0; second < width; second++) {
                positions.add(axis == Direction.Axis.X
                        ? origin.offset(0, first, second)
                        : origin.offset(first, second, 0));
            }
        }
        return List.copyOf(positions);
    }

    // Get the next horizontal square tank layer above or below its controller
    public static List<BlockPos> tankExtension(BlockPos controller, int width,
                                               int height, Direction placedFace) {
        if (controller == null || placedFace == null || placedFace.getAxis() != Direction.Axis.Y
                || width <= 1 || height <= 0) return List.of();
        BlockPos origin = placedFace == Direction.DOWN
                ? controller.below() : controller.above(height);
        List<BlockPos> positions = new ArrayList<>(width * width);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) positions.add(origin.offset(x, 0, z));
        }
        return List.copyOf(positions);
    }
}
