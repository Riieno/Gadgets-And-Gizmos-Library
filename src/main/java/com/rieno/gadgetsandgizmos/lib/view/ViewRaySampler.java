package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Share one bounded sensor sample between every consumer of the same live lens
public final class ViewRaySampler{
    private final ViewRayBudget budget = new ViewRayBudget();
    private long tick = Long.MIN_VALUE;
    private List<ViewRaycast.Result> rays = List.of();
    private ViewRaycast.Result nearest = ViewRaycast.Result.miss(net.minecraft.world.phys.Vec3.ZERO, 0);

    public void sample(Level level, ViewPose pose, double length, double rate, ViewRaycast.Filter filter, UUID ignored){
        if(level == null || level.isClientSide || tick == level.getGameTime()) return;
        tick = level.getGameTime();
        int count = budget.next(tick, rate);
        if(count == 0 && rate > 0) return;
        double range = Double.isFinite(length) ? Math.clamp(length, 0, ViewRaycast.MAX_LENGTH) : 0;
        nearest = ViewRaycast.Result.miss(pose.position().add(pose.forward().scale(range)), range);
        List<ViewRaycast.Result> samples = new ArrayList<>();
        for(int idx = 0; idx < count; idx++){
            var hit = ViewRaycast.trace(level, pose, range, filter, null, idx, count, ignored);
            samples.add(hit);
            if(hit.hit() && (!nearest.hit() || hit.distance() < nearest.distance())) nearest = hit;
        }
        rays = List.copyOf(samples);
    }

    public Map<String, GraphValue> values(Level level){
        return Map.of("ray_cast", GraphValue.list(rays.stream().map(hit -> hit.details(level)).toList()),
                "hit", GraphValue.bool(nearest.hit()), "hit_x", GraphValue.number(nearest.position().x),
                "hit_y", GraphValue.number(nearest.position().y), "hit_z", GraphValue.number(nearest.position().z),
                "hit_details", nearest.details(level), "is_sub_level", GraphValue.bool(nearest.subLevelId() != null));
    }
}
