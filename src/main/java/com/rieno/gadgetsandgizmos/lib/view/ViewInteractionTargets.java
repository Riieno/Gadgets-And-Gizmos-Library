package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Let projected controls supply their actual geometry without adding a block collider
public final class ViewInteractionTargets{
    private static final Map<BlockEntity, Picker> SURFACES = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private ViewInteractionTargets(){}

    // Register on both logical sides when the block entity loads; do not capture the entity in the picker
    public static void register(BlockEntity entity, Picker picker){ SURFACES.put(entity, picker); }

    // Select the nearest control before any accepted block or entity obstruction
    public static @Nullable Target pick(Level level, RaycastViewSource source, ViewReference reference, float partialTick, @Nullable UUID actor){
        return pick(level, source, reference, source.viewPose(partialTick), actor);
    }
    // Use the visible first-person aim without changing the source used by server validation
    public static @Nullable Target pick(Level level, RaycastViewSource source, ViewReference reference, ViewPose pose, @Nullable UUID actor){
        double range = source.interactionRange();
        var ray = ViewRaycast.traceInteraction(level, pose, range, source.interactionFilter(), reference, actor);
        Target best = null;
        if("block".equals(ray.kind())){
            Direction face = Direction.byName(ray.face());
            if(face != null) best = new Target(new ViewReference(level.dimension().location(), ray.subLevelId(), ray.localBlockPos()),
                    ray.position(), face, ray.distance(), true);
        }
        double limit = Math.min(range, ray.distance() + 0.00001);
        java.util.List<Map.Entry<BlockEntity, Picker>> surfaces;
        synchronized(SURFACES){ surfaces = SURFACES.entrySet().stream().map(entry -> Map.entry(entry.getKey(), entry.getValue())).toList(); }
        for(var entry : surfaces){
            BlockEntity entity = entry.getKey();
            if(entity.isRemoved() || entity.getLevel() == null){ SURFACES.remove(entity); continue; }
            if(entity.getLevel().isClientSide != level.isClientSide
                    || !entity.getLevel().dimension().equals(level.dimension())
                    || !source.interactionFilter().accepts(entity.getBlockState(), SableLevelApi.containingId(entity))) continue;
            Target target = entry.getValue().pick(entity, pose, limit);
            if(target == null || !Double.isFinite(target.distance()) || target.distance() < 0 || target.distance() > limit
                    || !target.reference().equals(ViewReference.of(entity))) continue;
            best = target;
            limit = target.distance();
        }
        return best;
    }

    @FunctionalInterface public interface Picker{
        @Nullable Target pick(BlockEntity entity, ViewPose pose, double range);
    }

    // Carry world geometry separately from the local block hit needed by native use handlers
    public record Target(ViewReference reference, Vec3 position, Direction face, double distance, boolean useBlock){
        public BlockHitResult localHit(Level level){
            Vec3 local = SableTransformApi.toLocalPosition(SableLevelApi.subLevel(level, reference.subLevelId()), position);
            return new BlockHitResult(local, face, reference.blockPos(), false);
        }
    }
}
