package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import dev.ryanhcode.sable.SableConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

// Match visual effects to their owner's world range rather than its distant plot coordinates
public final class ClientRenderDistance{
    private ClientRenderDistance(){}

    // Measure physical distance for displays on both world blocks and moving sublevels
    public static double distanceSquared(BlockEntity owner){
        Minecraft mc = Minecraft.getInstance();
        if(owner == null || owner.isRemoved() || mc.level == null || owner.getLevel() == null) return Double.POSITIVE_INFINITY;
        var body = SableLevelApi.containing(owner);
        if(body != null && body.isRemoved()) return Double.POSITIVE_INFINITY;
        if((body == null ? owner.getLevel() : body.getLevel()) != mc.level) return Double.POSITIVE_INFINITY;
        Vec3 pos = SableTransformApi.toWorldPosition(body, owner.getBlockPos().getCenter());
        return mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(pos);
    }

    public static boolean within(BlockEntity owner, double maximum){
        Minecraft mc = Minecraft.getInstance();
        if(owner == null || owner.isRemoved() || mc.level == null || owner.getLevel() == null) return false;
        var subLevel = SableLevelApi.containing(owner);
        if(subLevel != null && subLevel.isRemoved()) return false;
        var level = subLevel == null ? owner.getLevel() : subLevel.getLevel();
        if(level != mc.level) return false;
        Vec3 pos = SableTransformApi.toWorldPosition(subLevel, owner.getBlockPos().getCenter());
        double range = subLevel == null ? Math.max(2, mc.options.getEffectiveRenderDistance()) * 16D
                : SableConfig.SUB_LEVEL_TRACKING_RANGE.get();
        return within(mc.gameRenderer.getMainCamera().getPosition(), pos, Math.min(range, maximum), subLevel != null);
    }

    // World chunks use horizontal range; Sable tracks sublevels by spatial distance
    public static boolean within(Vec3 camera, Vec3 pos, double range, boolean spatial){
        if(camera == null || pos == null || !Double.isFinite(range) || range < 0) return false;
        double x = Math.abs(pos.x - camera.x), y = Math.abs(pos.y - camera.y), z = Math.abs(pos.z - camera.z);
        return spatial ? x * x + y * y + z * z <= range * range : Math.max(x, z) <= range;
    }
}
