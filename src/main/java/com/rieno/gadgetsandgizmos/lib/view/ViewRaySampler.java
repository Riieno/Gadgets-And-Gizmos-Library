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
    private ViewPose lens = new ViewPose(net.minecraft.world.phys.Vec3.ZERO, new org.joml.Quaterniond(), 70);
    private double range;

    public void sample(Level level, ViewPose pose, double length, double rate, ViewRaycast.Filter filter, UUID ignored){
        if(level == null || level.isClientSide || tick == level.getGameTime()) return;
        tick = level.getGameTime();
        lens = pose;
        this.range = Double.isFinite(length) ? Math.clamp(length, 0, ViewRaycast.MAX_LENGTH) : 0;
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
        var vals = new java.util.LinkedHashMap<>(nearest.values(level, lens, range));
        vals.put("ray_cast", GraphValue.list(rays.stream().map(hit -> hit.details(level, lens, range)).toList()));
        return Map.copyOf(vals);
    }
}
