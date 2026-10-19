package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// Move attached blocks through Sable's normal block entity and state transfer
public final class SubLevelAttachmentApi{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ThreadLocal<Deque<Movement>> MOVEMENTS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<SubLevelAssemblyHelper.AssemblyTransform, BoundingBox3ic> MOVED_BOUNDS = new WeakHashMap<>();

    private SubLevelAttachmentApi(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Include neighboring attachments without gathering unrelated support blocks
    public static List<BlockPos> includeAttachments(ServerLevel level, Iterable<BlockPos> blocks){
        Set<BlockPos> selected = new LinkedHashSet<>();
        for(BlockPos pos : blocks) selected.add(pos.immutable());
        Deque<BlockPos> frontier = new ArrayDeque<>(selected);
        while(!frontier.isEmpty()){
            BlockPos pos = frontier.removeFirst();
            for(Direction dir : Direction.values()){
                BlockPos candidate = pos.relative(dir);
                if(selected.contains(candidate) || !level.hasChunkAt(candidate)) continue;
                BlockState state = level.getBlockState(candidate);
                if(state.getBlock() instanceof SubLevelBlockAttachment attachment
                        && attachment.isAttachedTo(state, dir.getOpposite())){
                    selected.add(candidate);
                    frontier.addLast(candidate);
                }
            }
        }
        return List.copyOf(selected);
    }

    // Protect support checks while source and destination blocks are being transferred
    public static void moveBlocks(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                  Iterable<BlockPos> blocks, Runnable transfer){
        Set<BlockPos> source = new LinkedHashSet<>();
        Set<BlockPos> destination = new LinkedHashSet<>();
        for(BlockPos pos : blocks){
            source.add(pos.immutable());
            destination.add(transform.apply(pos).immutable());
        }
        Deque<Movement> movements = MOVEMENTS.get();
        movements.push(new Movement(level, transform.getLevel(), source, destination));
        try{
            transfer.run();
            if(!source.isEmpty()) MOVED_BOUNDS.put(transform, BoundingBox3i.from(source));
        }finally{
            movements.pop();
            if(movements.isEmpty()) MOVEMENTS.remove();
        }
    }

    // Keep attached tracking points inside the bounds passed to Sable's tracking transfer
    public static BoundingBox3ic trackingBounds(SubLevelAssemblyHelper.AssemblyTransform transform,
                                               BoundingBox3ic bounds){
        BoundingBox3ic moved = MOVED_BOUNDS.remove(transform);
        if(moved == null) return bounds;
        return new BoundingBox3i(Math.min(bounds.minX(), moved.minX()), Math.min(bounds.minY(), moved.minY()),
                Math.min(bounds.minZ(), moved.minZ()), Math.max(bounds.maxX(), moved.maxX()),
                Math.max(bounds.maxY(), moved.maxY()), Math.max(bounds.maxZ(), moved.maxZ()));
    }

    // Check whether a support position is participating in the current transfer
    public static boolean isMoving(Level level, BlockPos pos){
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if(serverLevel == null) return false;
        Deque<Movement> movements = MOVEMENTS.get();
        if(movements.isEmpty()){
            MOVEMENTS.remove();
            return false;
        }
        for(Movement movement : movements){
            if((movement.sourceLevel == serverLevel && movement.source.contains(pos))
                    || (movement.destinationLevel == serverLevel && movement.destination.contains(pos))) return true;
        }
        return false;
    }

    // Store both sides of one active transfer
    private record Movement(ServerLevel sourceLevel, ServerLevel destinationLevel,
                            Set<BlockPos> source, Set<BlockPos> destination){}
}
