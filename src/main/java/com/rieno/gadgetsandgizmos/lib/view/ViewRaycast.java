package com.rieno.gadgetsandgizmos.lib.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelTelemetryApi;
import com.rieno.gadgetsandgizmos.lib.probe.LoadedTerrainAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Query loaded world and SubLevel geometry with one common hit contract
public final class ViewRaycast{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public static final double MAX_LENGTH = 1024.0D;
    public static final int MAX_RAYS = 64;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent construction of the query facade
    private ViewRaycast(){}

    // Trace a deterministic sample across the lens field of view
    public static Result trace(Level level, ViewPose pose, double length, Filter filter,
                               @Nullable ViewReference excluded, int sample, int count){
        return trace(level, pose, length, filter, excluded, sample, count, true);
    }
    // Trace surface geometry without querying entities for projected lighting
    public static Result traceBlocks(Level level, ViewPose pose, double length,
                                     @Nullable ViewReference excluded, int sample, int count){
        return trace(level, pose, length, new Filter(Set.of("blocks", "sub_levels"), true),
                excluded, sample, count, false);
    }
    // Share loaded terrain traversal between sensing and surface projection
    private static Result trace(Level level, ViewPose pose, double length, Filter filter,
                                @Nullable ViewReference excluded, int sample, int count, boolean entities){
        double range = Double.isFinite(length) ? Math.clamp(length, 0, MAX_LENGTH) : 0;
        Vec3 dir = sampleDirection(pose, sample, Math.clamp(count, 1, MAX_RAYS));
        Vec3 start = pose.position();
        Vec3 end = start.add(dir.scale(range));
        Result best = Result.miss(end, range);
        if(range == 0 || level == null) return best;
        best = traceSpace(level, null, start, end, filter, excluded, best, entities);
        for(SubLevel body : SableTransformApi.intersecting(level, new AABB(start, end).inflate(1))){
            best = traceSpace(body.getLevel(), body, start, end, filter, excluded, best, entities);
        }
        return best;
    }

    // Distribute additional rays around the central lens ray
    public static Vec3 sampleDirection(ViewPose pose, int sample, int count){
        if(sample <= 0 || count <= 1) return pose.forward();
        double radius = Math.sqrt(sample / (double) Math.max(1, count - 1));
        double angle = sample * 2.399963229728653D;
        double spread = Math.tan(Math.toRadians(pose.fov() * 0.5D)) * radius;
        var dir = pose.orientation().transform(new org.joml.Vector3d(
                Math.cos(angle) * spread, Math.sin(angle) * spread, -1)).normalize();
        return new Vec3(dir.x, dir.y, dir.z);
    }

    // Compare shape and entity hits in one loaded coordinate space
    private static Result traceSpace(Level level, @Nullable SubLevel body, Vec3 start, Vec3 end,
                                     Filter filter, @Nullable ViewReference excluded, Result best, boolean entities){
        Vec3 from = SableTransformApi.toLocalPosition(body, start);
        Vec3 to = SableTransformApi.toLocalPosition(body, end);
        UUID id = body == null ? null : body.getUniqueId();
        LoadedTerrainAccess terrain = new LoadedTerrainAccess();
        terrain.beginTick(level);
        BlockGetter shapes = new LoadedBlocks(level, terrain);
        Result block = BlockGetter.traverseBlocks(from, to, terrain, (access, pos) -> {
            if(excluded != null && java.util.Objects.equals(id, excluded.subLevelId())
                    && pos.equals(excluded.blockPos())) return null;
            if(body == null && SableLevelApi.containing(level, pos) != null) return null;
            BlockState state = access.blockState(pos);
            if(state == null || state.isAir() || !filter.accepts(state, id)) return null;
            BlockHitResult hit = state.getShape(shapes, pos).clip(from, to, pos);
            if(hit == null) return null;
            Vec3 world = SableTransformApi.toWorldPosition(body, hit.getLocation());
            return new Result("block", world, start.distanceTo(world), pos.immutable(), state,
                    id, "", "", hit.getDirection().getSerializedName());
        }, access -> null);
        if(block != null && block.distance() <= best.distance()) best = block;
        if(!entities) return best;
        for(Entity entity : level.getEntities((Entity) null, new AABB(from, to).inflate(1),
                entity -> entity.isAlive() && entity.isPickable())){
            SubLevel owner = SableLevelApi.containing(entity);
            if(!java.util.Objects.equals(id, owner == null ? null : owner.getUniqueId())) continue;
            SubLevel tracked = owner == null ? SableLevelApi.tracking(entity) : owner;
            UUID hitId = tracked == null ? null : tracked.getUniqueId();
            if(!filter.accepts(entity, hitId)) continue;
            AABB bounds = entity.getBoundingBox().inflate(entity.getPickRadius());
            var hit = bounds.contains(from) ? java.util.Optional.of(from) : bounds.clip(from, to);
            if(hit.isEmpty()) continue;
            Vec3 world = SableTransformApi.toWorldPosition(body, hit.get());
            double distance = start.distanceTo(world);
            if(distance > best.distance() || distance == best.distance() && best.hit()) continue;
            best = new Result("entity", world, distance,
                    BlockPos.containing(SableTransformApi.toLocalPosition(tracked, world)), null, hitId,
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                    entity.getUUID().toString(), "");
        }
        return best;
    }

    // Keep neighboring shape reads inside immediately available chunks
    private record LoadedBlocks(Level level, LoadedTerrainAccess terrain) implements BlockGetter{
        // Read block entities only after their owning chunk is available
        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos){
            return terrain.blockState(pos) == null ? null : level.getBlockEntity(pos);
        }
        // Treat unavailable neighbors as empty geometry
        @Override
        public BlockState getBlockState(BlockPos pos){
            BlockState state = terrain.blockState(pos);
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }
        // Resolve fluid geometry from the same loaded state
        @Override
        public FluidState getFluidState(BlockPos pos){ return getBlockState(pos).getFluidState(); }
        // Retain the owning level's build height
        @Override
        public int getHeight(){ return level.getHeight(); }
        // Retain the owning level's minimum build height
        @Override
        public int getMinBuildHeight(){ return level.getMinBuildHeight(); }
    }

    // Identify independently selectable ray target categories
    public enum Category{
        SUB_LEVELS("sub_levels", "Sub-levels"),
        PASSIVE_MOBS("passive_mobs", "Passive Mobs"),
        HOSTILE_MOBS("hostile_mobs", "Hostile Mobs"),
        PLAYERS("players", "Players"),
        BLOCKS("blocks", "Blocks");

        private final String id;
        private final String label;
        // Retain the saved category identifier and its selector label
        Category(String id, String label){ this.id = id; this.label = label; }
        // Get the stable graph and saved filter identifier
        public String id(){ return id; }
        // Get the category's selector label
        public String label(){ return label; }
        // List selector identifiers in their presentation order
        public static java.util.List<String> optionIds(){
            return java.util.Arrays.stream(values()).map(Category::id).toList();
        }
        // Resolve a saved identifier or a human readable category name
        public static @Nullable Category find(String val){
            for(Category category : values()){
                if(category.id.equalsIgnoreCase(val) || category.label.equalsIgnoreCase(val)) return category;
            }
            return null;
        }
    }

    // Match categories while retaining block IDs, tags, entity IDs and body UUIDs
    public record Filter(Set<String> entries, boolean allowlist){
        // Retain immutable filter entries
        public Filter{ entries = Set.copyOf(entries); }
        // Accept filter text or graph lists without depending on addon serialization
        public static Filter fromValue(GraphValue val, boolean allowlist){
            Set<String> entries = new java.util.LinkedHashSet<>();
            Iterable<?> values = val.value() instanceof Iterable<?> list ? list : java.util.List.of(val.asString());
            for(Object item : values){
                String text = item instanceof GraphValue entry ? entry.asString() : String.valueOf(item);
                for(String part : text.split("[,;]+")){
                    Category category = Category.find(part.strip());
                    if(category != null){ entries.add(category.id()); continue; }
                    for(String entry : part.split("\\s+")){
                        if(!entry.isBlank()) entries.add(entry.strip());
                    }
                }
            }
            return new Filter(entries, allowlist);
        }
        // Accept or reject one block at a geometric hit
        public boolean accepts(BlockState state, @Nullable UUID subLevelId){
            boolean matches = entries.contains(subLevelId == null ? Category.BLOCKS.id() : Category.SUB_LEVELS.id())
                    || entries.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                    || subLevelId != null && entries.contains(subLevelId.toString());
            for(String entry : entries){
                if(!entry.startsWith("#")) continue;
                ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
                if(id != null && state.is(TagKey.create(net.minecraft.core.registries.Registries.BLOCK, id))) matches = true;
            }
            return allowlist == matches;
        }
        // Accept or reject one entity at a geometric hit
        public boolean accepts(Entity entity, @Nullable UUID subLevelId){
            boolean hostile = entity.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
            boolean matches = entity instanceof net.minecraft.world.entity.player.Player && entries.contains(Category.PLAYERS.id())
                    || entity instanceof net.minecraft.world.entity.Mob
                    && entries.contains(hostile ? Category.HOSTILE_MOBS.id() : Category.PASSIVE_MOBS.id())
                    || subLevelId != null && entries.contains(Category.SUB_LEVELS.id())
                    || entries.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
                    || entries.contains(entity.getUUID().toString())
                    || subLevelId != null && entries.contains(subLevelId.toString());
            for(String entry : entries){
                if(!entry.startsWith("#")) continue;
                ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
                if(id != null && entity.getType().is(TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, id))) matches = true;
            }
            return allowlist == matches;
        }
    }

    // Carry the nearest accepted hit with world and plot coordinates
    public record Result(String kind, Vec3 position, double distance, BlockPos localBlockPos,
                         @Nullable BlockState blockState, @Nullable UUID subLevelId,
                         String entityType, String entityId, String face){
        // Retain a separate immutable local hit position
        public Result{ localBlockPos = localBlockPos.immutable(); }
        // Build an explicit miss at the ray endpoint
        public static Result miss(Vec3 end, double length){
            return new Result("miss", end, length, BlockPos.containing(end), null, null, "", "", "");
        }
        // Check whether a block or entity was hit
        public boolean hit(){ return !"miss".equals(kind); }
        // Export detailed hit data through the existing immutable graph value API
        public GraphValue details(){
            Map<String, GraphValue> vals = new LinkedHashMap<>();
            vals.put("type", GraphValue.string(kind));
            vals.put("distance", GraphValue.number(distance));
            vals.put("position", vector(position));
            vals.put("local_block_position", vector(Vec3.atLowerCornerOf(localBlockPos)));
            vals.put("is_sub_level", GraphValue.bool(subLevelId != null));
            vals.put("sub_level", GraphValue.string(subLevelId == null ? "" : subLevelId.toString()));
            vals.put("face", GraphValue.string(face));
            vals.put("entity_type", GraphValue.string(entityType));
            vals.put("entity_uuid", GraphValue.string(entityId));
            vals.put("block", GraphValue.string(blockState == null ? ""
                    : BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString()));
            Map<String, GraphValue> properties = new LinkedHashMap<>();
            if(blockState != null) blockState.getValues().forEach((key, val) ->
                    properties.put(key.getName(), GraphValue.string(val instanceof StringRepresentable serialized
                            ? serialized.getSerializedName() : val.toString())));
            vals.put("block_state", GraphValue.map(properties));
            return GraphValue.map(vals);
        }
        // Include the loaded body's transform and physics without retaining its implementation
        public GraphValue details(Level level){
            Map<String, GraphValue> vals = new LinkedHashMap<>();
            ((Map<?, ?>) details().value()).forEach((key, val) -> vals.put(key.toString(), (GraphValue) val));
            vals.put("dimension", GraphValue.string(level.dimension().location().toString()));
            SubLevel body = SableLevelApi.subLevel(level, subLevelId);
            Map<String, GraphValue> subLevel = new LinkedHashMap<>();
            if(body != null && !body.isRemoved()){
                var pose = body.logicalPose();
                var rotation = pose.orientation();
                subLevel.put("uuid", GraphValue.string(body.getUniqueId().toString()));
                subLevel.put("position", vector(new Vec3(pose.position().x(), pose.position().y(), pose.position().z())));
                subLevel.put("orientation", GraphValue.map(Map.of("x", GraphValue.number(rotation.x()),
                        "y", GraphValue.number(rotation.y()), "z", GraphValue.number(rotation.z()),
                        "w", GraphValue.number(rotation.w()))));
                vals.put("local_hit_position", vector(SableTransformApi.toLocalPosition(body, position)));
                var physics = SableSubLevelTelemetryApi.sample(SableLevelApi.serverLevel(level), subLevelId);
                subLevel.put("mass", GraphValue.number(physics.mass()));
                subLevel.put("velocity", vector(physics.linearVelocity()));
                subLevel.put("angular_velocity", vector(physics.angularVelocity()));
            }else vals.put("local_hit_position", vector(position));
            vals.put("sub_level_details", GraphValue.map(subLevel));
            return GraphValue.map(vals);
        }
    }

    // Export one vector as typed coordinate values
    public static GraphValue vector(Vec3 pos){
        return GraphValue.map(Map.of("x", GraphValue.number(pos.x),
                "y", GraphValue.number(pos.y), "z", GraphValue.number(pos.z)));
    }
}
