package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodePresentationRegistry.Section;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

// Read server-side wearer telemetry independently of items and controller blocks
public final class EntityTelemetryApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<net.minecraft.resources.ResourceLocation, java.util.function.Function<Entity, Map<String, GraphValue>>> DETAILS = new LinkedHashMap<>();
    private EntityTelemetryApi(){
    }

    // Register addon-owned identity details without exposing addon entity classes
    public static void registerDetails(net.minecraft.resources.ResourceLocation id,
            java.util.function.Function<Entity, Map<String, GraphValue>> provider){
        if(DETAILS.putIfAbsent(java.util.Objects.requireNonNull(id), java.util.Objects.requireNonNull(provider)) != null)
            throw new IllegalArgumentException("Entity telemetry provider is already registered: " + id);
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
                "spectator", "using_item", "wearing_elytra", "elytra_flight"}) ports.put(port, "boolean");
        for(String port : new String[]{"look_block", "look_face", "look_sub_level", "main_hand", "off_hand"}){
            ports.put(port, "string");
        }
        return com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder.copy(ports);
    }

    // Describe identity and tracker fields inside an entity details MAP
    public static Map<String, String> detailPorts(){
        Map<String, String> fields = new LinkedHashMap<>(ports());
        for(String key : List.of("available", "holding_player", "on_lectern", "is_entity", "is_player", "is_mannequin", "is_armor_stand")) fields.put(key, "boolean");
        for(String key : List.of("entity_type", "entity_name", "entity_uuid", "dimension", "sub_level",
                "wearer_type", "wearer_name", "wearer_uuid", "player_name", "player_uuid")) fields.put(key, "string");
        for(String key : List.of("x", "y", "z")) fields.put(key, "number");
        for(String key : List.of("player_facing", "looking_at")) fields.put(key, "map");
        return com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder.copy(fields);
    }

    // Group shared telemetry by the type of subject it describes
    public static List<Section> sections(){
        return List.of(
                new Section("entity", "Entity", List.of("available", "is_entity", "entity_type", "entity_name", "entity_uuid",
                        "is_mannequin", "is_armor_stand", "wearer_type", "wearer_name", "wearer_uuid", "entity_details",
                        "health", "max_health", "absorption", "armor", "air", "alive", "on_ground", "sneaking", "sprinting",
                        "swimming", "in_water", "on_fire", "using_item", "main_hand", "off_hand", "wearing_elytra", "elytra_flight")),
                new Section("player", "Player", List.of("is_player", "holding_player", "on_lectern", "player_name", "player_uuid",
                        "player_facing", "food", "saturation", "flying", "creative", "spectator")),
                new Section("motion", "Position & Motion", List.of("position", "eye_position", "x", "y", "z", "velocity", "speed",
                        "distance", "relative_angle", "yaw", "pitch", "fall_distance", "dimension", "same_dimension")),
                new Section("look", "Look", List.of("looking_at", "looking_at_block", "look_hit", "look_block_position", "look_distance", "look_block", "look_face")),
                new Section("sub_level", "Sub-level", List.of("is_sub_level", "sub_level", "sub_level_details", "looking_at_sub_level",
                        "look_sub_level", "look_sub_level_position", "look_sub_level_distance")));
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
        Vec3 velocity = EntityMotionTelemetry.velocity(wearer);
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
        values.put("wearing_elytra", GraphValue.bool(wearer.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)));
        values.put("elytra_flight", GraphValue.bool(wearer.isFallFlying()));
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

    // Include tracker identity and movement for any loaded entity without retaining it
    public static Map<String, GraphValue> sampleEntity(@Nullable Entity target, Vec3 origin,
            Vec3 forward, Vec3 up, String dimension, double range){
        boolean available = target != null && !target.isRemoved() && !target.level().isClientSide;
        Map<String, GraphValue> vals = new LinkedHashMap<>(sample(
                available && target instanceof LivingEntity living ? living : null, origin, forward, up, dimension, range));
        boolean player = available && target instanceof Player;
        boolean stand = available && target instanceof ArmorStand;
        String type = available ? BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString() : "";
        String name = available ? target.getDisplayName().getString() : "";
        String id = available ? target.getUUID().toString() : "";
        vals.put("available", GraphValue.bool(available));
        vals.put("is_entity", GraphValue.bool(available));
        vals.put("holding_player", GraphValue.bool(player));
        vals.put("on_lectern", GraphValue.bool(false));
        vals.put("is_player", GraphValue.bool(player));
        vals.put("is_armor_stand", GraphValue.bool(stand));
        vals.put("is_mannequin", GraphValue.bool(false));
        vals.put("entity_type", GraphValue.string(type));
        vals.put("entity_name", GraphValue.string(name));
        vals.put("entity_uuid", GraphValue.string(id));
        vals.put("wearer_type", GraphValue.string(player ? "player" : stand ? "armor_stand" : available ? "entity" : ""));
        vals.put("wearer_name", GraphValue.string(name));
        vals.put("wearer_uuid", GraphValue.string(id));
        vals.put("player_name", GraphValue.string(player ? name : ""));
        vals.put("player_uuid", GraphValue.string(player ? id : ""));
        vals.put("dimension", GraphValue.string(available ? target.level().dimension().location().toString() : ""));
        var body = available ? SableLevelApi.tracking(target) : null;
        vals.put("sub_level", GraphValue.string(body == null ? "" : body.getUniqueId().toString()));
        Vec3 pos = available ? SableTransformApi.toWorldPosition(SableLevelApi.containing(target), target.position()) : Vec3.ZERO;
        if(available && !(target instanceof LivingEntity)){
            Vec3 velocity = EntityMotionTelemetry.velocity(target);
            boolean sameDimension = target.level().dimension().location().toString().equals(dimension);
            vals.put("position", vector(pos));
            vals.put("eye_position", vector(target.getEyePosition()));
            vals.put("velocity", vector(velocity));
            vals.put("speed", GraphValue.number(velocity.length()));
            vals.put("same_dimension", GraphValue.bool(sameDimension));
            vals.put("distance", GraphValue.number(sameDimension ? origin.distanceTo(pos) : 0));
            vals.put("relative_angle", angles(sameDimension ? TrackingGeometry.relativeAngles(origin, pos, forward, up) : new TrackingGeometry.Angles(0, 0)));
            vals.put("yaw", GraphValue.number(target.getYRot()));
            vals.put("pitch", GraphValue.number(target.getXRot()));
            vals.put("on_ground", GraphValue.bool(target.onGround()));
            vals.put("in_water", GraphValue.bool(target.isInWater()));
            vals.put("on_fire", GraphValue.bool(target.isOnFire()));
            vals.put("alive", GraphValue.bool(target.isAlive()));
        }
        vals.put("x", GraphValue.number(pos.x));
        vals.put("y", GraphValue.number(pos.y));
        vals.put("z", GraphValue.number(pos.z));
        vals.put("player_facing", vector(available ? target.getLookAngle() : Vec3.ZERO));
        vals.put("looking_at", vals.get("look_block_position"));
        if(available) DETAILS.values().forEach(provider -> vals.putAll(provider.apply(target)));
        return Map.copyOf(vals);
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
