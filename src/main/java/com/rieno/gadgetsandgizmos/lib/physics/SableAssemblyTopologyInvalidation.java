package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// Share topology revisions so every controller can reuse its assembly until something changes
public final class SableAssemblyTopologyInvalidation {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ServerLevel, Long> REVISIONS = new WeakHashMap<>();
    private static final Set<SubLevelContainer> OBSERVED_CONTAINERS =
            Collections.newSetFromMap(new WeakHashMap<>());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable assembly topology invalidation
    private SableAssemblyTopologyInvalidation() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the revision
    public static synchronized long revision(@Nullable ServerLevel level) {
        if (level == null) {
            return Long.MIN_VALUE;
        }
        observe(level);
        return REVISIONS.computeIfAbsent(level, ignored -> 0L);
    }

    // Invalidate the sable assembly topology invalidation
    public static synchronized long invalidate(@Nullable ServerLevel level) {
        if (level == null) {
            return Long.MIN_VALUE;
        }
        observe(level);
        long next = REVISIONS.getOrDefault(level, 0L) + 1L;
        if (next == Long.MIN_VALUE) {
            next = 1L;
        }
        REVISIONS.put(level, next);
        return next;
    }

    // Invalidate the sable assembly topology invalidation
    public static long invalidate(@Nullable ServerSubLevel subLevel) {
        return invalidate(subLevel == null ? null : subLevel.getLevel());
    }

    // Invalidate the sable assembly topology invalidation
    public static long invalidate(@Nullable Level level) {
        return invalidate(SableLevelApi.serverLevel(level));
    }

    // Forget the cached topology revision
    public static synchronized void forget(@Nullable ServerLevel level) {
        if (level != null) {
            REVISIONS.remove(level);
        }
    }

    // Watch for sublevel topology changes
    private static void observe(ServerLevel level) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !OBSERVED_CONTAINERS.add(container)) {
            return;
        }
        container.addObserver(new SubLevelObserver() {
            // Handle the sublevel added event
            @Override
            public void onSubLevelAdded(SubLevel subLevel) {
                invalidate(level);
            }

            // Handle the sublevel removed event
            @Override
            public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
                invalidate(level);
            }
        });
    }
}
