package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyHullApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.probe.LoadedTerrainAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;

// Resolve player and SubLevel ray hits into live world bounds without loading missing targets
public final class ViewTrackingTargets{
    public static final double ACQUISITION_RADIUS = 1.5;
    public static final double ACQUISITION_HALF_ANGLE = 15;
    private static final int MAX_CONE_CANDIDATES = 32;
    private ViewTrackingTargets(){}

    // Supplement precise ray hits with nearby visible targets in a widening acquisition volume
    public static List<ViewTargetLock.Target<Reference>> acquire(Level level, ViewPose pose, double length,
            ViewRaycast.Filter filter, @Nullable ViewReference excluded, List<ViewRaycast.Result> samples){
        UUID excludedBody = excluded == null ? null : excluded.subLevelId();
        List<ViewTargetLock.Target<Reference>> targets = new ArrayList<>(hits(level, samples, filter, excludedBody));
        double range = Double.isFinite(length) ? Math.clamp(length, 0, ViewRaycast.MAX_LENGTH) : 0;
        targets.addAll(coneHits(level, new ViewAcquisitionCone(pose.position(), pose.forward(), range,
                ACQUISITION_RADIUS, ACQUISITION_HALF_ANGLE), filter, excluded));
        return List.copyOf(targets);
    }

    // Test loaded player and body bounds before confirming an actual filtered geometric hit
    public static List<ViewTargetLock.Target<Reference>> coneHits(Level level, ViewAcquisitionCone cone,
            ViewRaycast.Filter filter, @Nullable ViewReference excluded){
        if(level == null || level.isClientSide || cone.range() <= 0) return List.of();
        var root = SableLevelApi.serverLevel(level);
        if(root == null) return List.of();
        UUID excludedBody = excluded == null ? null : excluded.subLevelId();
        List<Candidate> candidates = new ArrayList<>();
        for(Player player : root.players()){
            var target = resolve(root, new Reference(player.getUUID(), null, null), filter, excludedBody);
            if(target != null) addCandidate(candidates, cone, target);
        }
        for(var body : SableTransformApi.intersecting(root, cone.bounds())){
            if(body.getUniqueId().equals(excludedBody)) continue;
            var bounds = SableAssemblyHullApi.sample(body, List.of(body)).worldBounds();
            if(!bounds.isEmpty()) addCandidate(candidates, cone,
                    new ViewTargetLock.Target<>(new Reference(null, body.getUniqueId(), null), bounds.getFirst()));
        }
        candidates.sort(Comparator.comparingDouble(candidate ->
                ViewTargetLock.distanceSquared(cone.origin(), candidate.target().bounds())));
        for(Candidate candidate : candidates.subList(0, Math.min(MAX_CONE_CANDIDATES, candidates.size()))){
            for(Vec3 point : probePoints(candidate)){
                if(!cone.contains(point) || point.distanceToSqr(cone.origin()) < 1.0E-12) continue;
                double length = Math.min(cone.range(), cone.origin().distanceTo(point) + .05);
                ViewRaycast.Result hit = ViewRaycast.traceToward(root, cone.origin(), point, length, filter, excluded);
                Reference ref = candidate.target().key();
                boolean matches = ref.playerId() != null ? "entity".equals(hit.kind())
                        && ref.playerId().toString().equals(hit.entityId())
                        : "block".equals(hit.kind()) && ref.bodyId().equals(hit.subLevelId());
                if(!matches || !cone.contains(hit.position())) continue;
                var targets = hits(root, List.of(hit), filter, excludedBody);
                if(!targets.isEmpty()) return targets;
            }
        }
        return List.of();
    }

    private static void addCandidate(List<Candidate> candidates, ViewAcquisitionCone cone,
                                     ViewTargetLock.Target<Reference> target){
        Vec3 point = cone.point(target.bounds());
        if(point != null) candidates.add(new Candidate(target, point));
    }

    // Probe both the volume intersection and other visible parts of hollow or partly covered targets
    private static List<Vec3> probePoints(Candidate candidate){
        List<Vec3> points = new ArrayList<>();
        points.add(candidate.point());
        AABB bounds = candidate.target().bounds();
        Vec3 center = bounds.getCenter();
        points.add(center);
        if(candidate.target().key().playerId() != null){
            points.add(new Vec3(center.x, bounds.maxY - bounds.getYsize() * .15, center.z));
            return points;
        }
        for(int idx = 0; idx < 8; idx++){
            Vec3 corner = new Vec3((idx & 1) == 0 ? bounds.minX : bounds.maxX,
                    (idx & 2) == 0 ? bounds.minY : bounds.maxY, (idx & 4) == 0 ? bounds.minZ : bounds.maxZ);
            points.add(center.lerp(corner, .85));
        }
        return points;
    }

    private record Candidate(ViewTargetLock.Target<Reference> target, Vec3 point){}

    // Convert only supported ray hits, leaving selection and lock lifetime to the caller
    public static List<ViewTargetLock.Target<Reference>> hits(Level level, List<ViewRaycast.Result> hits,
            ViewRaycast.Filter filter, @Nullable UUID excludedBody){
        List<ViewTargetLock.Target<Reference>> targets = new ArrayList<>();
        for(ViewRaycast.Result hit : hits){
            Reference ref = null;
            if("entity".equals(hit.kind())){
                try{ ref = new Reference(UUID.fromString(hit.entityId()), null, null); }
                catch(IllegalArgumentException err){ continue; }
            }else if("block".equals(hit.kind()) && hit.subLevelId() != null){
                ref = new Reference(null, hit.subLevelId(), hit.localBlockPos());
            }
            if(ref == null) continue;
            var target = resolve(level, ref, filter, excludedBody);
            if(target != null) targets.add(target);
        }
        return List.copyOf(targets);
    }

    // Recheck the original category filter and current geometry while retaining only an identity
    public static @Nullable ViewTargetLock.Target<Reference> resolve(Level level, Reference ref,
            ViewRaycast.Filter filter, @Nullable UUID excludedBody){
        if(level == null || level.isClientSide || ref == null) return null;
        var root = SableLevelApi.serverLevel(level);
        if(root == null) return null;
        if(ref.playerId() != null){
            Player player = root.getPlayerByUUID(ref.playerId());
            if(player == null || !player.isAlive() || player.isSpectator() || !player.isPickable()) return null;
            var owner = SableLevelApi.containing(player);
            var carried = owner == null ? SableLevelApi.tracking(player) : owner;
            if(!filter.accepts(player, carried == null ? null : carried.getUniqueId())) return null;
            AABB local = player.getBoundingBox();
            AABB world = null;
            for(int idx = 0; idx < 8; idx++){
                var point = SableTransformApi.toWorldPosition(owner, new net.minecraft.world.phys.Vec3(
                        (idx & 1) == 0 ? local.minX : local.maxX, (idx & 2) == 0 ? local.minY : local.maxY,
                        (idx & 4) == 0 ? local.minZ : local.maxZ));
                AABB corner = new AABB(point, point);
                world = world == null ? corner : world.minmax(corner);
            }
            return new ViewTargetLock.Target<>(ref, world);
        }
        if(ref.bodyId() == null || ref.bodyId().equals(excludedBody) || ref.blockPos() == null) return null;
        var body = SableLevelApi.subLevel(root, ref.bodyId());
        if(body == null || body.isRemoved()) return null;
        LoadedTerrainAccess terrain = new LoadedTerrainAccess();
        terrain.beginTick(body.getLevel());
        var state = terrain.blockState(ref.blockPos());
        if(state == null || state.isAir() || !filter.accepts(state, ref.bodyId())) return null;
        var bounds = SableAssemblyHullApi.sample(body, List.of(body)).worldBounds();
        return bounds.isEmpty() ? null : new ViewTargetLock.Target<>(ref, bounds.getFirst());
    }

    // Identify a player or a block hit on a body without retaining either implementation
    public record Reference(@Nullable UUID playerId, @Nullable UUID bodyId, @Nullable BlockPos blockPos){
        public Reference{ if(blockPos != null) blockPos = blockPos.immutable(); }
    }
}
