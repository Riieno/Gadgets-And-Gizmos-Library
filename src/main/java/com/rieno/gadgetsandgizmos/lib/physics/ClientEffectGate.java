package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

// Common callers can skip visual work without loading client renderer classes on a server
public final class ClientEffectGate{
    private static Predicate<BlockEntity> range = owner -> false;
    private ClientEffectGate(){}

    public static void installRange(Predicate<BlockEntity> probe){ range = java.util.Objects.requireNonNull(probe); }

    public static boolean withinRange(BlockEntity owner){
        return owner != null && owner.getLevel() != null && owner.getLevel().isClientSide && range.test(owner);
    }

    // Trace the output corridor in world space, including the emitter's own moving hull
    public static boolean clearOutput(BlockEntity owner, Vec3 localOrigin, Vec3 localDirection, double distance){
        if(owner == null || owner.getLevel() == null || !owner.getLevel().isClientSide) return false;
        var subLevel = SableLevelApi.containing(owner);
        var level = subLevel == null ? owner.getLevel() : subLevel.getLevel();
        Vec3 start = SableTransformApi.toWorldPosition(subLevel, localOrigin);
        Vec3 dir = SableTransformApi.toWorldDirection(subLevel, localDirection);
        double length = Math.max(0, distance - .002D);
        return SubLevelParticleOcclusion.findBlockingDistance(level, subLevel, start, dir, length, true,
                java.util.Set.of(), true) >= length;
    }
}
