package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Project a server-owned block light onto loaded world or sublevel terrain
public final class ProjectedLightSource implements AutoCloseable{
    private final TransientLightBeam light;

    // Use an invisible light block whose scheduled tick expires unowned sources
    public ProjectedLightSource(BlockState state){ light = new TransientLightBeam(state); }
    // Offset the source towards the lens so ordinary block lighting can leave the hit face
    public void update(Level level, ViewPose pose, double length, @Nullable ViewReference excluded){
        if(level == null || level.isClientSide){ close(); return; }
        ViewRaycast.Result hit = ViewRaycast.traceBlocks(level, pose, length, excluded, 0, 1);
        if(!hit.hit()){ close(); return; }
        Vec3 point = sourcePosition(pose.position(), hit.position());
        var body = SableLevelApi.subLevel(level, hit.subLevelId());
        Level target = body == null ? level : body.getLevel();
        light.updatePoint(target, SableTransformApi.toLocalPosition(body, point));
        Direction face = Direction.byName(hit.face());
        if(light.positions().isEmpty() && face != null){
            light.updatePoint(target, hit.localBlockPos().relative(face).getCenter());
        }
    }
    // Keep short rays between the surface and lens while offsetting longer rays by half a block
    public static Vec3 sourcePosition(Vec3 lens, Vec3 hit){
        Vec3 delta = lens.subtract(hit);
        double distance = delta.length();
        return distance < 1.0E-8D ? hit : hit.add(delta.scale(Math.min(0.5D, distance) / distance));
    }
    // Remove only the light positions still owned by this emitter
    @Override
    public void close(){ light.clear(); }
}
