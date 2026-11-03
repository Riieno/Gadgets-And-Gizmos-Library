package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.navigation.NavigationMapData;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

// Query loaded nearby craft through Sable's spatial index and collapse connected actors into one marker
public final class SableMapMarkers{
    private SableMapMarkers(){}

    public static List<NavigationMapData.Marker> capture(ServerLevel level, Vec3 center, Set<UUID> excluded, double range){
        return capture(level, center, excluded, range, body -> true);
    }

    // Select eligible bodies before choosing each connected craft's marker and applying the marker limit
    public static List<NavigationMapData.Marker> capture(ServerLevel level, Vec3 center, Set<UUID> excluded, double range,
                                                       Predicate<? super SubLevel> eligible){
        if(level == null || !NavigationMapData.finite(center) || !Double.isFinite(range) || range <= 0) return List.of();
        double radius = Math.min(512, range);
        var seen = new HashSet<>(excluded == null ? Set.<UUID>of() : excluded);
        var markers = new ArrayList<NavigationMapData.Marker>();
        var nearby = SableTransformApi.intersecting(level, new AABB(center, center).inflate(radius));
        for(SubLevel candidate : nearby){
            if(!seen.add(candidate.getUniqueId())) continue;
            var chain = new ArrayList<SubLevel>();
            var chainIds = new HashSet<UUID>();
            chain.add(candidate);
            chainIds.add(candidate.getUniqueId());
            for(SubLevel body : SubLevelHelper.getConnectedChain(candidate)){
                if(body != null && !body.isRemoved() && body.getLevel() == level && chainIds.add(body.getUniqueId())) chain.add(body);
            }
            boolean own = excluded != null && chain.stream().anyMatch(body -> excluded.contains(body.getUniqueId()));
            chain.forEach(body -> seen.add(body.getUniqueId()));
            if(own) continue;
            SubLevel root = chain.stream().filter(eligible).max(Comparator.comparingDouble(SableMapMarkers::volume)
                    .thenComparing(body -> body.getUniqueId().toString())).orElse(null);
            if(root == null) continue;
            var bounds = SableAssemblyHullApi.worldBounds(root);
            if(bounds == null) continue;
            Vec3 pos = bounds.getCenter();
            if(pos.distanceToSqr(center) > radius * radius) continue;
            Vec3 forward = SableTransformApi.toWorldDirection(root, new Vec3(0, 0, -1));
            markers.add(new NavigationMapData.Marker(root.getUniqueId(), pos, Math.toDegrees(Math.atan2(forward.x, -forward.z))));
        }
        return markers.stream().sorted(Comparator.comparingDouble((NavigationMapData.Marker marker) -> marker.position().distanceToSqr(center))
                .thenComparing(marker -> marker.id().toString())).limit(NavigationMapData.MAX_SHIPS).toList();
    }

    private static double volume(SubLevel body){
        var bounds = SableAssemblyHullApi.worldBounds(body);
        return bounds == null ? 0 : bounds.getXsize() * bounds.getYsize() * bounds.getZsize();
    }
}
