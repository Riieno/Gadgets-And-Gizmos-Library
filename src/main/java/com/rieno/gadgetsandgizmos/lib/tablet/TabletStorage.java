package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

// Store each tablet with isolated app data and atomic updates
public interface TabletStorage {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the tablet
    CompoundTag tablet(UUID tabletId);

    // Get the app
    CompoundTag app(UUID tabletId, ResourceLocation appId);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the installed apps
    Set<ResourceLocation> installedApps(UUID tabletId);

    // Update the tablet
    CompoundTag updateTablet(UUID tabletId, UnaryOperator<CompoundTag> mutation);

    // Update the app
    CompoundTag updateApp(UUID tabletId, ResourceLocation appId,
                          UnaryOperator<CompoundTag> mutation);

    // Set the installed
    boolean setInstalled(UUID tabletId, ResourceLocation appId, boolean installed);

    // Get the shared
    default CompoundTag shared(UUID tabletId, TabletAppDefinition owner, String key) {
        if (tabletId == null || owner == null || !owner.shares(key)) return new CompoundTag();
        return app(tabletId, owner.id()).getCompound("Shared").getCompound(key).copy();
    }

    // Update the shared
    default CompoundTag updateShared(UUID tabletId, TabletAppDefinition owner, String key,
                                     UnaryOperator<CompoundTag> mutation) {
        if (tabletId == null || owner == null || mutation == null || !owner.shares(key)) {
            return new CompoundTag();
        }
        final CompoundTag[] published = {new CompoundTag()};
        updateApp(tabletId, owner.id(), data -> {
            CompoundTag shared = data.getCompound("Shared").copy();
            CompoundTag prev = shared.getCompound(key).copy();
            CompoundTag next = mutation.apply(prev);
            if (next == null) next = prev;
            shared.put(key, next.copy());
            data.put("Shared", shared);
            published[0] = next.copy();
            return data;
        });
        return published[0].copy();
    }
}
