package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

// Propagate held angles through compatible Create kinetic networks
public final class HeldAngleKineticGraph {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Owned targets
    private final Map<BlockPos, KineticBlockEntity> ownedTargets = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the held angle kinetic graph
    public ApplyResult apply(Level level, @Nullable KineticBlockEntity seedTarget, float seedAngleDegrees,
                             Set<BlockPos> claimedTargets, Predicate<KineticBlockEntity> skipNode,
                             KineticTargetSynchronizer synchronizer) {
        Map<BlockPos, KineticBlockEntity> nextTargets = new HashMap<>();
        if (seedTarget == null || claimedTargets.contains(seedTarget.getBlockPos())) {
            syncTargets(level, nextTargets, claimedTargets, synchronizer);
            return ApplyResult.EMPTY;
        }

        ArrayDeque<HeldAngleNode> frontier = new ArrayDeque<>();
        Set<BlockPos> localVisited = new HashSet<>();
        frontier.addLast(new HeldAngleNode(seedTarget, seedAngleDegrees));
        float stressBase = 0.0f;

        while (!frontier.isEmpty()) {
            HeldAngleNode node = frontier.removeFirst();
            KineticBlockEntity current = node.target();
            if (skipNode.test(current)) {
                continue;
            }
            if (!GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(current)) {
                continue;
            }

            BlockPos currentPos = current.getBlockPos().immutable();
            if (!localVisited.add(currentPos) || claimedTargets.contains(currentPos)) {
                continue;
            }

            if (KineticAngleHelper.publishRotationAngle(current, node.angleDegrees())) {
                synchronizer.sync(current);
            }

            nextTargets.put(currentPos, current);
            claimedTargets.add(currentPos);
            stressBase += Math.max(current.calculateStressApplied(), 0.0f);

            if (current instanceof PreciseKineticOutputBoundary) {
                continue;
            }

            for (KineticBlockEntity neighbour : KineticGraphHelper.getConnectedNeighbours(current)) {
                if (skipNode.test(neighbour)) {
                    continue;
                }
                if (!GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(neighbour)) {
                    continue;
                }
                BlockPos neighbourPos = neighbour.getBlockPos();
                if (claimedTargets.contains(neighbourPos) || localVisited.contains(neighbourPos)) {
                    continue;
                }
                Float modifier = KineticGraphHelper.getRotationSpeedModifier(current, neighbour);
                if (modifier == null || Math.abs(modifier) < 1.0E-4f) {
                    continue;
                }
                frontier.addLast(new HeldAngleNode(neighbour, node.angleDegrees() * modifier));
            }
        }

        syncTargets(level, nextTargets, claimedTargets, synchronizer);
        return new ApplyResult(nextTargets.size(), stressBase);
    }

    // Clear the held angle kinetic graph
    public void clear(Level level, KineticTargetSynchronizer synchronizer) {
        if (ownedTargets.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, KineticBlockEntity> target : ownedTargets.entrySet()) {
            clearHeldAngle(level, target.getKey(), target.getValue(), synchronizer);
        }
        ownedTargets.clear();
    }

    // Sync the targets
    private void syncTargets(Level level, Map<BlockPos, KineticBlockEntity> nextTargets, Set<BlockPos> claimedTargets,
                             KineticTargetSynchronizer synchronizer) {
        Set<BlockPos> staleTargets = new HashSet<>(ownedTargets.keySet());
        staleTargets.removeAll(nextTargets.keySet());
        for (BlockPos staleTarget : staleTargets) {
            if (claimedTargets.contains(staleTarget)) {
                continue;
            }
            clearHeldAngle(level, staleTarget, ownedTargets.get(staleTarget), synchronizer);
        }

        ownedTargets.clear();
        ownedTargets.putAll(nextTargets);
    }

    // Clear the held angle
    static void clearHeldAngle(Level level, BlockPos targetPos, @Nullable KineticBlockEntity ownedTarget,
                               KineticTargetSynchronizer synchronizer) {
        KineticBlockEntity target = ownedTarget;
        if (target == null || target.getLevel() != level || target.isRemoved()) {
            BlockEntity blockEntity = level.getBlockEntity(targetPos);
            target = blockEntity instanceof KineticBlockEntity kinetic ? kinetic : null;
        }
        if (target == null || !KineticAngleHelper.clearHeldAngles(target)) {
            return;
        }
        synchronizer.sync(target);
    }

    // Store apply results
    public record ApplyResult(int claimedTargetCount, float stressBase) {
        public static final ApplyResult EMPTY = new ApplyResult(0, 0.0f);
    }

    // Expose the kinetic target synchronizer
    @FunctionalInterface
    public interface KineticTargetSynchronizer {
        // Sync the kinetic target synchronizer
        void sync(KineticBlockEntity target);
    }

    // Store the held angle node
    private record HeldAngleNode(KineticBlockEntity target, float angleDegrees) {
    }
}
