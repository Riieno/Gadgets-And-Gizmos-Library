package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Project a widening, fading light footprint without changing world lighting or blocks
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class SurfaceFloodlight implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Set<SurfaceFloodlight> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private Level level;
    private List<Patch> patches = List.of();
    private long sampledTick = Long.MIN_VALUE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Increase the footprint with distance while distributing the same light energy
    public static Profile profile(double distance){
        double val = Double.isFinite(distance) ? Math.clamp(distance, 0, ViewRaycast.MAX_LENGTH) : ViewRaycast.MAX_LENGTH;
        double radius = Math.clamp(0.35D + val * 0.2D, 0.35D, 12);
        double brightness = 1.0D / (1.0D + val * val / 1600.0D);
        return new Profile(radius, brightness, Math.max(2, (int) Math.round(15 * brightness)));
    }
    // Refresh ray samples on the client without loading renderer classes on the server
    public void update(Level level, ViewPose pose, double length, @Nullable ViewReference excluded){
        if(level == null || !level.isClientSide){ close(); return; }
        if(this.level != level){ close(); this.level = level; }
        long tick = level.getGameTime();
        if(sampledTick != Long.MIN_VALUE && tick >= sampledTick && tick - sampledTick < 4) return;
        sampledTick = tick;
        double range = Double.isFinite(length) ? Math.clamp(length, 0, ViewRaycast.MAX_LENGTH) : 0;
        ViewRaycast.Result center = ViewRaycast.traceBlocks(level, pose, range, excluded, 0, 1);
        if(!center.hit()){ clear(); return; }
        Profile profile = profile(center.distance());
        double fov = Math.toDegrees(2 * Math.atan(profile.radius() / Math.max(0.35D, center.distance())));
        ViewPose cone = new ViewPose(pose.position(), pose.orientation(), Math.clamp(fov, 5, 120));
        List<Patch> wanted = new ArrayList<>();
        Set<Surface> visited = new LinkedHashSet<>();
        int count = profile.radius() < 1 ? 1 : 25;
        for(int idx = 0; idx < count; idx++){
            ViewRaycast.Result hit = idx == 0 ? center : ViewRaycast.traceBlocks(level, cone, range, excluded, idx, count);
            if(!hit.hit()) continue;
            var body = SableLevelApi.subLevel(level, hit.subLevelId());
            Level target = body == null ? level : body.getLevel();
            Direction face = Direction.byName(hit.face());
            if(face == null) continue;
            Profile sample = profile(hit.distance());
            Vec3 local = SableTransformApi.toLocalPosition(body, hit.position());
            int spread = count == 1 ? 1 : Math.max(1, (int) Math.ceil(sample.radius() / 3));
            Direction u = face.getAxis() == Direction.Axis.X ? Direction.UP : Direction.EAST;
            Direction v = face.getAxis() == Direction.Axis.Z ? Direction.UP : Direction.SOUTH;
            for(int x = -spread; x <= spread; x++) for(int y = -spread; y <= spread; y++){
                BlockPos pos = hit.localBlockPos().relative(u, x).relative(v, y);
                if(!target.hasChunkAt(pos) || target.getBlockState(pos).isAir()
                        || target.getBlockState(pos.relative(face)).isSolidRender(target, pos.relative(face))) continue;
                if(pos.getCenter().distanceToSqr(local) > Math.pow(sample.radius() + 1, 2)) continue;
                Surface surface = new Surface(hit.subLevelId(), pos.immutable(), face);
                if(!visited.add(surface)) continue;
                wanted.add(new Patch(hit.subLevelId(), pos.immutable(), face,
                        SableTransformApi.toLocalPosition(body, center.position()), profile));
            }
        }
        patches = List.copyOf(wanted);
        ACTIVE.add(this);
    }
    // Expose loaded surface patches to a client renderer without owning its world state
    public static List<Patch> patches(Level level){
        List<Patch> res = new ArrayList<>();
        for(SurfaceFloodlight light : ACTIVE) if(light.level == level) res.addAll(light.patches);
        return List.copyOf(res);
    }
    // Remove the footprint immediately when the beam misses or is disabled
    private void clear(){
        patches = List.of();
        if(level != null && level.isClientSide) ACTIVE.remove(this);
    }
    // Release all lighting when the owning source unloads
    @Override
    public void close(){ clear(); level = null; sampledTick = Long.MIN_VALUE; }
    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload evt){
        if(!(evt.getLevel() instanceof Level level) || !level.isClientSide) return;
        for(SurfaceFloodlight light : List.copyOf(ACTIVE)) if(light.level == level) light.close();
    }

    public record Profile(double radius, double brightness, int light){
        // Fade each surface vertex within the projected footprint
        public double intensity(double distance){
            if(!Double.isFinite(distance) || distance < 0) return 0;
            double fraction = distance / radius;
            return brightness * Math.max(0, 1 - fraction * fraction);
        }
    }
    public record Patch(@Nullable UUID subLevelId, BlockPos pos, Direction face, Vec3 center, Profile profile){}
    private record Surface(@Nullable UUID subLevelId, BlockPos pos, Direction face){}
}
