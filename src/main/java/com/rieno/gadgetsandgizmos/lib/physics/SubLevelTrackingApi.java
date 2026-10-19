package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.Arrays;

// Find loaded bodies without requiring a particular mod's naming system
public final class SubLevelTrackingApi {
    private SubLevelTrackingApi(){
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Find bodies by world distance and optional exact names, excluding the observer's body
    public static List<Target> scan(@Nullable ServerLevel level, Vec3 origin, Vec3 forward,
                                    Vec3 up, @Nullable UUID excludedId, String filter,
                                    double range, Function<SubLevel, String> names){
        return scan(level, origin, forward, up, excludedId, filter, range, names, body -> true);
    }

    // Apply caller supplied eligibility before matching names or reporting detections
    public static List<Target> scan(@Nullable ServerLevel level, Vec3 origin, Vec3 forward,
                                    Vec3 up, @Nullable UUID excludedId, String filter,
                                    double range, Function<SubLevel, String> names,
                                    Predicate<SubLevel> eligible){
        if(level == null || !Double.isFinite(range) || range < 0.0D) return List.of();
        String requested = filter == null ? "" : filter.strip();
        boolean all = requested.isBlank() || "all".equalsIgnoreCase(requested);
        Set<String> selected = Arrays.stream(requested.split(","))
                .map(String::strip).filter(name -> !name.isBlank()).collect(Collectors.toSet());
        List<Target> targets = new ArrayList<>();
        for(SubLevel body : SableLevelApi.subLevels(level)){
            if(body.isRemoved() || body.getUniqueId().equals(excludedId)) continue;
            var pos = body.logicalPose().position();
            Vec3 world = new Vec3(pos.x(), pos.y(), pos.z());
            double distance = origin.distanceTo(world);
            if(!Double.isFinite(distance) || distance > range) continue;
            if(eligible != null && !eligible.test(body)) continue;
            String name = names == null ? "" : names.apply(body);
            name = name == null ? "" : name;
            if(!all && (name.isBlank() || !selected.contains(name))) continue;
            targets.add(new Target(body.getUniqueId(), name, world, distance,
                    TrackingGeometry.relativeAngles(origin, world, forward, up)));
        }
        targets.sort(Comparator.comparingDouble(Target::distance)
                .thenComparing(target -> target.id().toString()));
        return List.copyOf(targets);
    }

    // Store one world-space body detection
    public record Target(UUID id, String name, Vec3 position, double distance,
                         TrackingGeometry.Angles angles){
    }
}
