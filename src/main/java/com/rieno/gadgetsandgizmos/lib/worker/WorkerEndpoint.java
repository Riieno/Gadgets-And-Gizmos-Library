package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

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

    // Check whether the backing endpoint is currently loaded and usable
    default boolean isAvailable(){
        return true;
    }

    // Distinguish storage destinations from stations that only consume recipe ingredients
    default boolean acceptsDelivery(){
        return true;
    }

    // Permit an explicitly routed ingredient or fuel transfer into a machine input
    default boolean acceptsProcessingInput(){
        return false;
    }

    // Prefer existing stock or an explicit matching filter before empty general storage
    default int insertionPriority(WorkerResourceKey resource){
        return available(resource) > 0L ? 0 : 1;
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

    // Select an item variant by its actual components, such as an empty portable tank.
    default WorkerResourcePacket extractMatchingItem(WorkerResourceKey resource, long maximumAmount,
                                                      Predicate<ItemStack> matches, boolean simulate){
        return WorkerResourcePacket.empty(resource);
    }

    default long availableMatchingItem(WorkerResourceKey resource, Predicate<ItemStack> matches){
        return 0L;
    }

    // Insert a serialized packet and return the accepted amount
    long insert(WorkerResourcePacket packet, boolean simulate);
}
