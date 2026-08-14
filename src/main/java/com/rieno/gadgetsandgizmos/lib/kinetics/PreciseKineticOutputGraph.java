package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

// Propagate exact output angles through compatible Create kinetic networks
public final class PreciseKineticOutputGraph {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Owned targets
    private final Set<BlockPos> ownedTargets = new HashSet<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the precise kinetic output graph
    public ApplyResult apply(KineticBlockEntity source, float sourceAngleDegrees,
                             Predicate<KineticBlockEntity> skipNode,
                             HeldAngleKineticGraph.KineticTargetSynchronizer synchronizer) {
        Level level = source.getLevel();
        if (level == null) {
            return ApplyResult.EMPTY;
        }

        Set<BlockPos> nextTargets = new HashSet<>();
        ArrayDeque<HeldAngleNode> frontier = new ArrayDeque<>();
        Set<BlockPos> localVisited = new HashSet<>();
        frontier.addLast(new HeldAngleNode(source, sourceAngleDegrees));
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
            if (!localVisited.add(currentPos)) {
                continue;
            }

            if (KineticAngleHelper.publishRotationAngle(current, node.angleDegrees())) {
                synchronizer.sync(current);
            }

            nextTargets.add(currentPos);
            stressBase += Math.max(current.calculateStressApplied(), 0.0f);

            if (current != source && current instanceof PreciseKineticOutputBoundary) {
                continue;
            }

            for (KineticBlockEntity neighbour : KineticGraphHelper.getConnectedNeighbours(current)) {
                if (skipNode.test(neighbour)
                        || !GadgetsNGizmosKineticGuard.shouldPropagatePreciseAngleTo(neighbour)
                        || !ct$isDownstreamOf(current, neighbour)) {
                    continue;
                }
                Float modifier = KineticGraphHelper.getRotationSpeedModifier(current, neighbour);
                if (modifier == null || Math.abs(modifier) < 1.0E-4f) {
                    continue;
                }
                frontier.addLast(new HeldAngleNode(neighbour, node.angleDegrees() * modifier));
            }
        }

        syncTargets(level, nextTargets, synchronizer);
        return new ApplyResult(nextTargets.size(), stressBase);
    }

    // Clear the precise kinetic output graph
    public void clear(@Nullable Level level, HeldAngleKineticGraph.KineticTargetSynchronizer synchronizer) {
        if (level == null || ownedTargets.isEmpty()) {
            return;
        }
        for (BlockPos targetPos : ownedTargets) {
            HeldAngleKineticGraph.clearHeldAngle(level, targetPos, synchronizer);
        }
        ownedTargets.clear();
    }

    // Sync the targets
    private void syncTargets(Level level, Set<BlockPos> nextTargets,
                             HeldAngleKineticGraph.KineticTargetSynchronizer synchronizer) {
        Set<BlockPos> staleTargets = new HashSet<>(ownedTargets);
        staleTargets.removeAll(nextTargets);
        for (BlockPos staleTarget : staleTargets) {
            HeldAngleKineticGraph.clearHeldAngle(level, staleTarget, synchronizer);
        }

        ownedTargets.clear();
        ownedTargets.addAll(nextTargets);
    }

    // Check if this is downstream of the target
    private static boolean ct$isDownstreamOf(KineticBlockEntity current, KineticBlockEntity neighbour) {
        return neighbour.hasSource()
                && neighbour.source != null
                && neighbour.source.equals(current.getBlockPos());
    }

    // Store apply results
    public record ApplyResult(int claimedTargetCount, float stressBase) {
        public static final ApplyResult EMPTY = new ApplyResult(0, 0.0f);
    }

    // Store the held angle node
    private record HeldAngleNode(KineticBlockEntity target, float angleDegrees) {
    }
}
