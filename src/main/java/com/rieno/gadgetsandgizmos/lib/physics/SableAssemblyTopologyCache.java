package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

// Cache one filtered assembly view across event revisions with a staggered 200-239 tick safety refresh
public final class SableAssemblyTopologyCache {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long SAFETY_REFRESH_TICKS = 200L;
    private static final long SAFETY_REFRESH_STAGGER_TICKS = 40L;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Filter
    private final SableAssemblyTopologyApi.ActorFilter filter;
    // Classifier
    private final SableAssemblyTopologyApi.ActorClassifier classifier;
    // Current level
    private @Nullable ServerLevel level;
    // Current root sub-level
    private @Nullable ServerSubLevel rootSubLevel;
    // Current root sub-level id
    private @Nullable UUID rootSubLevelId;
    // Current revision
    private long revision = Long.MIN_VALUE;
    // Next safety refresh tick
    private long nextSafetyRefreshTick = Long.MIN_VALUE;
    // Current generation
    private long generation;
    // Current topology
    private @Nullable SableAssemblyTopologyApi.Topology topology;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable assembly topology cache
    public SableAssemblyTopologyCache() {
        this(SableAssemblyTopologyApi.ActorFilter.ALL,
                SableAssemblyTopologyApi.ActorClassifier.STRUCTURAL);
    }

    // Initialize the sable assembly topology cache
    public SableAssemblyTopologyCache(
            @Nullable SableAssemblyTopologyApi.ActorFilter filter,
            @Nullable SableAssemblyTopologyApi.ActorClassifier classifier
    ) {
        this.filter = filter == null
                ? SableAssemblyTopologyApi.ActorFilter.ALL : filter;
        this.classifier = classifier == null
                ? SableAssemblyTopologyApi.ActorClassifier.STRUCTURAL : classifier;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the sable assembly topology cache value
    public synchronized SableAssemblyTopologyApi.Topology get(
            @Nullable ServerSubLevel root
    ) {
        ServerLevel nextLevel = root == null ? null : root.getLevel();
        UUID nextRootId = root == null ? null : root.getUniqueId();
        long nextRevision = SableAssemblyTopologyInvalidation.revision(nextLevel);
        long gameTime = nextLevel == null ? Long.MIN_VALUE : nextLevel.getGameTime();
        boolean safetyRefreshDue = nextLevel != null
                && gameTime >= nextSafetyRefreshTick;
        if (topology != null && level == nextLevel
                && rootSubLevel == root
                && Objects.equals(rootSubLevelId, nextRootId)
                && revision == nextRevision && root != null && !root.isRemoved()
                && !safetyRefreshDue) {
            return topology;
        }
        level = nextLevel;
        rootSubLevel = root;
        rootSubLevelId = nextRootId;
        revision = nextRevision;
        topology = SableAssemblyTopologyApi.discover(root, filter, classifier);
        nextSafetyRefreshTick = nextSafetyRefreshTick(gameTime, nextRootId);
        generation++;
        return topology;
    }

    // Invalidate the sable assembly topology cache
    public synchronized void invalidate() {
        topology = null;
        rootSubLevel = null;
        revision = Long.MIN_VALUE;
        nextSafetyRefreshTick = Long.MIN_VALUE;
    }

    // Get the generation
    public synchronized long generation() {
        return generation;
    }

    // Get the revision
    public synchronized long revision() {
        return revision;
    }

    // Calculate the next safety refresh tick
    private static long nextSafetyRefreshTick(long gameTime, @Nullable UUID rootId) {
        if (gameTime == Long.MIN_VALUE || rootId == null) {
            return Long.MAX_VALUE;
        }
        long mixedId = rootId.getMostSignificantBits()
                ^ Long.rotateLeft(rootId.getLeastSignificantBits(), 23);
        long stagger = Math.floorMod(mixedId, SAFETY_REFRESH_STAGGER_TICKS);
        return gameTime > Long.MAX_VALUE - SAFETY_REFRESH_TICKS - stagger
                ? Long.MAX_VALUE : gameTime + SAFETY_REFRESH_TICKS + stagger;
    }
}
