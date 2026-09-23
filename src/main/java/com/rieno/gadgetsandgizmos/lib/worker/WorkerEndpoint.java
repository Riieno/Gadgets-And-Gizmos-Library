package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

// Adapt one smart or manifested storage to the shared worker logistics surface
public interface WorkerEndpoint {
    // Get the stable endpoint id
    UUID id();

    // Get the containing sub-level id, or null for the root level
    @Nullable UUID subLevelId();

    // Get the local block position
    BlockPos position();

    // Get a human-readable endpoint label for worker activity and configuration UIs.
    default String label() {
        return position().toShortString();
    }

    // Get the loaded blocks a worker may approach to interact with this endpoint
    default List<BlockPos> interactionBlocks() {
        return List.of(position());
    }

    // Get the linker face preferred for worker interaction, when one was configured
    default @Nullable Direction interactionFace() {
        return null;
    }

    // Check whether this endpoint can supply the resource
    boolean canExtract(WorkerResourceKey resource);

    // Check whether this endpoint can receive the resource
    boolean canInsert(WorkerResourceKey resource);

    // Get the available resource amount
    long available(WorkerResourceKey resource);

    // Get the remaining capacity for the resource
    long space(WorkerResourceKey resource);

    // Extract a serialized packet
    WorkerResourcePacket extract(WorkerResourceKey resource, long maximumAmount, boolean simulate);

    // Insert a serialized packet and return the accepted amount
    long insert(WorkerResourcePacket packet, boolean simulate);
}
