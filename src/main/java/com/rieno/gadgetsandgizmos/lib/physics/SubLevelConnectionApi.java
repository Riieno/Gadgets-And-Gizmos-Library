package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Resolve and combine the sublevels linked through block entity actors
public final class SubLevelConnectionApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sublevel connection API
    private SubLevelConnectionApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve one live sublevel by ID
    public static @Nullable SubLevel resolve(@Nullable Level level, @Nullable UUID subLevelId) {
        if (level == null || subLevelId == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        SubLevel subLevel = container.getSubLevel(subLevelId);
        return subLevel == null || subLevel.isRemoved() ? null : subLevel;
    }

    // Combine live sublevel dependency lists
    @SafeVarargs
    public static List<SubLevel> merge(@Nullable Iterable<? extends SubLevel>... dependencies) {
        List<SubLevel> merged = new ArrayList<>();
        Set<SubLevel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (dependencies == null) {
            return List.of();
        }
        for (Iterable<? extends SubLevel> values : dependencies) {
            addAll(merged, seen, values);
        }
        return List.copyOf(merged);
    }

    // Collect sublevels linked to connected block entities
    public static List<SubLevel> connectedTo(Iterable<? extends BlockEntity> blockEntities) {
        List<SubLevel> dependencies = new ArrayList<>();
        Set<SubLevel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (blockEntities == null) {
            return List.of();
        }
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity == null || blockEntity.isRemoved()) {
                continue;
            }
            add(dependencies, seen, Sable.HELPER.getContaining(blockEntity));
            if (blockEntity instanceof BlockEntitySubLevelActor actor) {
                addAll(dependencies, seen, actor.sable$getConnectionDependencies());
            }
        }
        return List.copyOf(dependencies);
    }

    // Add every live dependency once
    private static void addAll(List<SubLevel> dependencies, Set<SubLevel> seen,
                               @Nullable Iterable<? extends SubLevel> values) {
        if (values == null) {
            return;
        }
        for (SubLevel subLevel : values) {
            add(dependencies, seen, subLevel);
        }
    }

    // Add one live dependency once
    private static void add(List<SubLevel> dependencies, Set<SubLevel> seen, @Nullable SubLevel subLevel) {
        if (subLevel != null && !subLevel.isRemoved() && seen.add(subLevel)) {
            dependencies.add(subLevel);
        }
    }
}
