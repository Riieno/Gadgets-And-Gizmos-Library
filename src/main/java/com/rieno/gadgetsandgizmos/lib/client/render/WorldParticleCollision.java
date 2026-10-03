package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelParticleOcclusion;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Set;
import java.util.function.Predicate;

// Clip client particle movement against loaded root and Sable block shapes
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID, value = Dist.CLIENT)
public final class WorldParticleCollision{
    // Share block shapes and the SubLevel snapshot across particles in one client tick
    private static final SubLevelParticleOcclusion.ProbeCache CACHE = new SubLevelParticleOcclusion.ProbeCache();
    private static final Predicate<BlockState> BLOCKS_VIEW = state -> state.canOcclude()
            && ItemBlockRenderTypes.getChunkRenderType(state) == RenderType.solid();
    private static ClientLevel cachedLevel;
    private static long cachedGameTime = Long.MIN_VALUE;

    // Prevent construction of the collision helper
    private WorldParticleCollision(){
    }

    // Return the movement allowed before the first solid block surface
    public static Vec3 clipMotion(ClientLevel level, Vec3 start, Vec3 motion){
        if(level == null || start == null || motion == null || motion.lengthSqr() < 1.0E-12D){
            return motion == null ? Vec3.ZERO : motion;
        }
        prepareCache(level);
        double distance = motion.length();
        double clearDistance = SubLevelParticleOcclusion.findBlockingDistance(
                level, null, start, motion.scale(1.0D / distance), distance,
                true, Set.of(), true, CACHE);
        return clearDistance >= distance ? motion : motion.scale(clearDistance / distance);
    }

    // Check whether solid world geometry hides a point from an observer
    public static boolean isOccluded(ClientLevel level, Vec3 observer, Vec3 target, double surfaceTolerance){
        if(level == null || observer == null || target == null) return false;
        Vec3 path = target.subtract(observer);
        double distance = path.length();
        if(distance < 1.0E-6D) return false;
        prepareCache(level);
        double clearDistance = SubLevelParticleOcclusion.findVisualBlockingDistance(
                level, observer, path.scale(1.0D / distance), distance, BLOCKS_VIEW, CACHE);
        return clearDistance + Math.max(0.0D, surfaceTolerance) < distance;
    }

    // Keep nearby traces on the same block shape snapshot
    private static void prepareCache(ClientLevel level){
        if(cachedLevel == level && cachedGameTime == level.getGameTime()) return;
        CACHE.clear();
        cachedLevel = level;
        cachedGameTime = level.getGameTime();
    }

    // Clip movement and find the face that stopped the particle
    public static MotionResult collide(ClientLevel level, Vec3 start, Vec3 motion){
        Vec3 allowed = clipMotion(level, start, motion);
        if(level == null || start == null || motion == null
                || allowed.distanceToSqr(motion) <= 1.0E-8D){
            return new MotionResult(allowed, Vec3.ZERO);
        }
        Vec3 contact = start.add(allowed);
        Vec3 normal = Vec3.ZERO;
        double strongest = 0.0D;
        double[] components = {motion.x, motion.y, motion.z};
        for(int axis = 0; axis < components.length; axis++){
            double component = components[axis];
            if(Math.abs(component) < 1.0E-6D) continue;
            double sign = Math.signum(component);
            Vec3 probe = axis == 0 ? new Vec3(sign * 0.125D, 0.0D, 0.0D)
                    : axis == 1 ? new Vec3(0.0D, sign * 0.125D, 0.0D)
                    : new Vec3(0.0D, 0.0D, sign * 0.125D);
            Vec3 clipped = clipMotion(level, contact, probe);
            if(clipped.lengthSqr() + 1.0E-8D >= probe.lengthSqr()
                    || Math.abs(component) <= strongest) continue;
            strongest = Math.abs(component);
            normal = probe.normalize().scale(-1.0D);
        }
        if(normal.lengthSqr() < 0.5D){
            double ax = Math.abs(motion.x);
            double ay = Math.abs(motion.y);
            double az = Math.abs(motion.z);
            normal = ax >= ay && ax >= az ? new Vec3(-Math.signum(motion.x), 0.0D, 0.0D)
                    : ay >= az ? new Vec3(0.0D, -Math.signum(motion.y), 0.0D)
                    : new Vec3(0.0D, 0.0D, -Math.signum(motion.z));
        }
        return new MotionResult(allowed, normal);
    }

    // Return a direction along the contact plane
    public static Vec3 tangentDirection(Vec3 normal, double angle){
        if(normal == null || normal.lengthSqr() < 1.0E-12D) return Vec3.ZERO;
        Vec3 unit = normal.normalize();
        Vec3 reference = Math.abs(unit.y) > 0.8D
                ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 first = unit.cross(reference).normalize();
        Vec3 second = unit.cross(first).normalize();
        return first.scale(Math.cos(angle)).add(second.scale(Math.sin(angle)));
    }

    public record MotionResult(Vec3 motion, Vec3 normal){
        public boolean collided(){
            return normal.lengthSqr() > 0.5D;
        }
    }

    // Release world references and shapes after all particles have moved
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post evt){
        CACHE.clear();
        cachedLevel = null;
        cachedGameTime = Long.MIN_VALUE;
    }
}
