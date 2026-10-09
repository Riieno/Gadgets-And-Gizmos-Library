package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

// Read server-side wearer telemetry independently of items and controller blocks
public final class EntityTelemetryApi {
    private EntityTelemetryApi(){
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Describe the additive telemetry ports for graph consumers
    public static Map<String, String> ports(){
        Map<String, String> ports = new LinkedHashMap<>();
        for(String port : new String[]{"position", "eye_position", "velocity", "look_hit", "look_block_position",
                "look_sub_level_position", "relative_angle"}) ports.put(port, "map");
        for(String port : new String[]{"distance", "look_distance", "look_sub_level_distance", "speed",
                "yaw", "pitch", "health", "max_health", "absorption", "armor", "air", "fall_distance",
                "food", "saturation"}) ports.put(port, "number");
        for(String port : new String[]{"looking_at_block", "looking_at_sub_level", "same_dimension", "on_ground",
                "sneaking", "sprinting", "swimming", "in_water", "on_fire", "alive", "flying", "creative",
                "spectator", "using_item"}) ports.put(port, "boolean");
        for(String port : new String[]{"look_block", "look_face", "look_sub_level", "main_hand", "off_hand"}){
            ports.put(port, "string");
        }
        return Map.copyOf(ports);
    }

    // Build correctly typed unavailable values
    public static Map<String, GraphValue> unavailable(){
        Map<String, GraphValue> values = new LinkedHashMap<>();
        ports().forEach((port, type) -> values.put(port, switch(type){
            case "map" -> "relative_angle".equals(port)
                    ? angles(new TrackingGeometry.Angles(0.0D, 0.0D)) : vector(Vec3.ZERO);
            case "boolean" -> GraphValue.bool(false);
            case "string" -> GraphValue.string("");
            default -> GraphValue.number(0.0D);
        }));
        return values;
    }

    // Sample movement, state and the closest visible block in world or loaded SubLevels
    public static Map<String, GraphValue> sample(@Nullable LivingEntity wearer, Vec3 origin,
                                                Vec3 forward, Vec3 up, String dimension, double range){
        Map<String, GraphValue> values = new LinkedHashMap<>(unavailable());
        if(wearer == null || wearer.level().isClientSide) return Map.copyOf(values);
        var feet = Sable.HELPER.getFeetPos(wearer, 0.0F);
        Vec3 pos = new Vec3(feet.x(), feet.y(), feet.z());
        Vec3 eye = wearer.getEyePosition();
        Vec3 velocity = wearer.getDeltaMovement().scale(20.0D);
        boolean sameDimension = wearer.level().dimension().location().toString().equals(dimension);
        values.put("same_dimension", GraphValue.bool(sameDimension));
        values.put("position", vector(pos));
        values.put("eye_position", vector(eye));
        values.put("velocity", vector(velocity));
        values.put("speed", GraphValue.number(velocity.length()));
        if(sameDimension){
            values.put("distance", GraphValue.number(origin.distanceTo(pos)));
            values.put("relative_angle", angles(TrackingGeometry.relativeAngles(origin, pos, forward, up)));
        }
        values.put("yaw", GraphValue.number(wearer.getYRot()));
        values.put("pitch", GraphValue.number(wearer.getXRot()));
        values.put("health", GraphValue.number(wearer.getHealth()));
        values.put("max_health", GraphValue.number(wearer.getMaxHealth()));
        values.put("absorption", GraphValue.number(wearer.getAbsorptionAmount()));
        values.put("armor", GraphValue.number(wearer.getArmorValue()));
        values.put("air", GraphValue.number(wearer.getAirSupply()));
        values.put("fall_distance", GraphValue.number(wearer.fallDistance));
        values.put("on_ground", GraphValue.bool(wearer.onGround()));
        values.put("sneaking", GraphValue.bool(wearer.isShiftKeyDown()));
        values.put("sprinting", GraphValue.bool(wearer.isSprinting()));
        values.put("swimming", GraphValue.bool(wearer.isSwimming()));
        values.put("in_water", GraphValue.bool(wearer.isInWater()));
        values.put("on_fire", GraphValue.bool(wearer.isOnFire()));
        values.put("alive", GraphValue.bool(wearer.isAlive()));
        values.put("using_item", GraphValue.bool(wearer.isUsingItem()));
        values.put("main_hand", GraphValue.string(BuiltInRegistries.ITEM.getKey(wearer.getMainHandItem().getItem()).toString()));
        values.put("off_hand", GraphValue.string(BuiltInRegistries.ITEM.getKey(wearer.getOffhandItem().getItem()).toString()));
        if(wearer instanceof Player player){
            values.put("food", GraphValue.number(player.getFoodData().getFoodLevel()));
            values.put("saturation", GraphValue.number(player.getFoodData().getSaturationLevel()));
            values.put("flying", GraphValue.bool(player.getAbilities().flying));
            values.put("creative", GraphValue.bool(player.isCreative()));
            values.put("spectator", GraphValue.bool(player.isSpectator()));
        }
        if(!Double.isFinite(range) || range <= 0.0D) return Map.copyOf(values);
        ClipContext ctx = new ClipContext(eye, eye.add(wearer.getLookAngle().scale(Math.min(range, 256.0D))),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, wearer);
        // The endpoints already use world coordinates; let Sable test every loaded body.
        if(ctx instanceof ClipContextExtension extension) extension.sable$setDoNotProject(true);
        BlockHitResult hit = wearer.level().clip(ctx);
        if(hit.getType() != HitResult.Type.BLOCK) return Map.copyOf(values);
        SubLevel body = SableLevelApi.containing(wearer.level(), hit.getBlockPos());
        Vec3 worldHit = SableTransformApi.toWorldPosition(body, hit.getLocation());
        Vec3 blockPos = SableTransformApi.toWorldPosition(body, hit.getBlockPos().getCenter());
        values.put("looking_at_block", GraphValue.bool(true));
        values.put("look_hit", vector(worldHit));
        values.put("look_block_position", vector(blockPos));
        values.put("look_distance", GraphValue.number(eye.distanceTo(worldHit)));
        values.put("look_block", GraphValue.string(BuiltInRegistries.BLOCK.getKey(
                wearer.level().getBlockState(hit.getBlockPos()).getBlock()).toString()));
        values.put("look_face", GraphValue.string(hit.getDirection().getName()));
        if(body != null && !body.isRemoved()){
            var bodyPos = body.logicalPose().position();
            Vec3 world = new Vec3(bodyPos.x(), bodyPos.y(), bodyPos.z());
            values.put("looking_at_sub_level", GraphValue.bool(true));
            values.put("look_sub_level", GraphValue.string(body.getUniqueId().toString()));
            values.put("look_sub_level_position", vector(world));
            values.put("look_sub_level_distance", GraphValue.number(eye.distanceTo(world)));
        }
        return Map.copyOf(values);
    }

    // Encode a world vector for any graph consumer
    public static GraphValue vector(Vec3 pos){
        return GraphValue.map(Map.of("x", GraphValue.number(pos.x), "y", GraphValue.number(pos.y),
                "z", GraphValue.number(pos.z)));
    }

    // Encode relative yaw and pitch in degrees
    public static GraphValue angles(TrackingGeometry.Angles angles){
        return GraphValue.map(Map.of("yaw", GraphValue.number(angles.yaw()),
                "pitch", GraphValue.number(angles.pitch())));
    }
}
