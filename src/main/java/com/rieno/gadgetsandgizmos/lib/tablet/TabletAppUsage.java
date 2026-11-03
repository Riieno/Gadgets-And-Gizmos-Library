package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Rank installed apps using launch counts stored with each tablet
public final class TabletAppUsage{
    private static final String USAGE_KEY = "AppUsage";

    private TabletAppUsage(){}

    // Count one launch through the owning storage implementation
    public static void recordLaunch(TabletStorage storage, UUID tabletId, ResourceLocation appId){
        if(storage == null || tabletId == null || appId == null) return;
        storage.updateTablet(tabletId, settings -> {
            CompoundTag usage = settings.getCompound(USAGE_KEY).copy();
            long count = Math.max(0L, usage.getLong(appId.toString()));
            usage.putLong(appId.toString(), count == Long.MAX_VALUE ? count : count + 1L);
            settings.put(USAGE_KEY, usage);
            return settings;
        });
    }

    // Keep the installed order for ties and leave removed apps out of quicklaunch
    public static List<TabletAppDefinition> mostUsed(List<TabletAppDefinition> installed,
                                                    CompoundTag settings, int limit){
        CompoundTag usage = settings.getCompound(USAGE_KEY);
        return installed.stream().sorted(Comparator.comparingLong((TabletAppDefinition app) ->
                        Math.max(0L, usage.getLong(app.id().toString()))).reversed())
                .limit(Math.max(0, limit)).toList();
    }
}
