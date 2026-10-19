package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Preserve declared connection configuration and remap its endpoints after construction
public final class SchematicLinkData{

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String KEY = "gadgetsngizmos:link_configuration";
    private static final String REF = "gadgetsngizmos:reference:";
    // Retain schemas registered by the owning integrations during common setup
    private static final Map<ResourceLocation, Registration> SCHEMAS = new LinkedHashMap<>();

    // Keep schema registration and conversion on the static API
    private SchematicLinkData(){}


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Describe the encoding used by one declared endpoint
    public enum PositionFormat{ LONG, BLOCK_POS, VECTOR, RELATIVE_VECTOR, BODY }

    // Locate an endpoint inside nested compounds or lists without guessing field semantics
    public record Reference(List<String> path, String positionKey, String subLevelKey, PositionFormat format){
        // Retain an independent path to the serialized endpoint
        public Reference{ path = List.copyOf(path); }
    }

    // Limit retained data to explicit configuration, identities and world-space geometry
    public record Schema(Set<String> keys, List<Reference> references, Set<String> identityKeys,
                         List<List<String>> worldPointLists, Set<String> detachedKeys, List<List<String>> fieldPaths){
        // Copy schema collections before exposing them to exporters
        public Schema{
            keys = Set.copyOf(keys); references = List.copyOf(references); identityKeys = Set.copyOf(identityKeys);
            worldPointLists = worldPointLists.stream().map(List::copyOf).toList(); detachedKeys = Set.copyOf(detachedKeys);
            fieldPaths = fieldPaths.stream().map(List::copyOf).toList();
        }
        // Preserve schemas which select complete top-level configuration fields
        public Schema(Set<String> keys, List<Reference> references, Set<String> identityKeys,
                      List<List<String>> worldPointLists, Set<String> detachedKeys){
            this(keys, references, identityKeys, worldPointLists, detachedKeys, List.of());
        }
        // Define endpoint-only configuration without world geometry or runtime identities
        public Schema(Set<String> keys, List<Reference> references){
            this(keys, references, Set.of(), List.of(), Set.of());
        }
    }

    // Retain one typed block predicate alongside its portable configuration schema
    private record Registration(Predicate<BlockEntity> matches, Schema schema){}

    // Register a strict integration-owned schema without replacing another provider
    public static synchronized void register(ResourceLocation id, Predicate<BlockEntity> matches, Schema schema){
        if(id == null || matches == null || schema == null) throw new IllegalArgumentException("Schematic link schema is missing");
        if(SCHEMAS.putIfAbsent(id, new Registration(matches, schema)) != null)
            throw new IllegalArgumentException("Schematic link schema is already registered: " + id);
    }

    // Remove declared runtime objects before loading a detached requirement or export model
    static CompoundTag detached(BlockEntity be, CompoundTag src){
        CompoundTag data = src.copy();
        for(Registration val : SCHEMAS.values()) if(val.matches().test(be)) val.schema().detachedKeys().forEach(data::remove);
        return data;
    }

    // Normalize only declared fields while retaining already portable configurations unchanged
    static void capture(BlockEntity be, CompoundTag src, CompoundTag safe, UUID sourceId,
                        Collection<SchematicConnectionData.BodyFrame> frames){
        if(src.contains(KEY, Tag.TAG_COMPOUND)){ safe.put(KEY, src.getCompound(KEY).copy()); return; }
        CompoundTag configurations = new CompoundTag();
        for(var entry : SCHEMAS.entrySet()){
            Registration registration = entry.getValue();
            if(!registration.matches().test(be)) continue;
            Schema schema = registration.schema();
            CompoundTag data = new CompoundTag();
            for(String key : schema.keys()){
                Tag val = src.get(key);
                if(val != null) data.put(key, val.copy());
            }
            for(List<String> path : schema.fieldPaths()) copyField(src, data, path);
            for(Reference ref : schema.references()) walk(data, ref.path(), tag -> normalize(tag, ref, be.getBlockPos(), sourceId, frames));
            pruneUnavailable(data);
            identities(data, schema.identityKeys(), id -> identity(id, frames.stream().map(SchematicConnectionData.BodyFrame::templateId).toList()));
            Vec3 origin = frames.stream().filter(frame -> java.util.Objects.equals(sourceId, frame.sourceId()))
                    .findFirst().map(SchematicConnectionData.BodyFrame::worldOrigin).orElse(Vec3.ZERO);
            for(List<String> path : schema.worldPointLists()) points(data, path, val -> val.subtract(origin));
            configurations.put(entry.getKey().toString(), data);
        }
        if(!configurations.isEmpty()) safe.put(KEY, configurations);
    }

    // Install declared connections after every destination body and block is available
    static void restore(ServerLevel level, BlockPos pos, CompoundTag saved,
                        SubLevelSchematicSerializationContext ctx, Vec3 target, Quaterniondc rotation){
        CompoundTag configurations = saved.getCompound(KEY);
        if(configurations.isEmpty()) return;
        BlockEntity be = level.getBlockEntity(pos);
        if(be == null) return;
        var prev = SubLevelSchematicSerializationContext.getCurrentContext();
        try{
            SubLevelSchematicSerializationContext.setCurrentContext(null);
            CompoundTag data = be.saveWithFullMetadata(level.registryAccess());
            for(var entry : SCHEMAS.entrySet()){
                if(!configurations.contains(entry.getKey().toString()) || !entry.getValue().matches().test(be)) continue;
                Schema schema = entry.getValue().schema();
                CompoundTag config = configurations.getCompound(entry.getKey().toString()).copy();
                for(Reference ref : schema.references()) walk(config, ref.path(), tag -> place(tag, ref, pos, ctx));
                identities(config, schema.identityKeys(), id -> identity(id,
                        ctx.getMappings().values().stream().map(val -> val.newUUID()).distinct().toList()));
                for(List<String> path : schema.worldPointLists()) points(config, path, val -> {
                    Vector3d point = rotation.transform(new Vector3d(val.x, val.y, val.z));
                    return target.add(point.x, point.y, point.z);
                });
                for(String key : schema.keys()){
                    data.remove(key);
                    Tag val = config.get(key);
                    if(val != null) data.put(key, val.copy());
                }
                for(List<String> path : schema.fieldPaths()) copyField(config, data, path);
            }
            be.loadWithComponents(data, level.registryAccess());
            be.setChanged();
            if(be instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart) smart.sendData();
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(prev); }
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain a source endpoint's owning template and fractional position
    private static void normalize(CompoundTag data, Reference ref, BlockPos owner, UUID sourceId,
                                  Collection<SchematicConnectionData.BodyFrame> frames){
        if(ref.format() == PositionFormat.BODY){
            if(!data.hasUUID(ref.positionKey()) && !(data.get(ref.positionKey()) instanceof StringTag)) return;
            UUID id = data.hasUUID(ref.positionKey()) ? data.getUUID(ref.positionKey()) : UUID.fromString(data.getString(ref.positionKey()));
            var frame = frames.stream().filter(val -> id.equals(val.sourceId())).findFirst().orElse(null);
            boolean stringId = data.get(ref.positionKey()) instanceof StringTag;
            data.remove(ref.positionKey());
            if(frame == null) return;
            CompoundTag endpoint = new CompoundTag(); endpoint.putUUID("Body", frame.templateId());
            endpoint.putBoolean("StringId", stringId); data.put(REF + ref.positionKey(), endpoint);
            return;
        }
        Tag raw = ref.positionKey().isEmpty() ? data.copy() : data.get(ref.positionKey());
        Vec3 point = position(raw, ref.format());
        if(point == null) return;
        if(ref.format() == PositionFormat.RELATIVE_VECTOR) point = point.add(Vec3.atLowerCornerOf(owner));
        UUID id = sourceId;
        boolean stringId = false;
        if(ref.subLevelKey() != null){
            if(data.hasUUID(ref.subLevelKey())) id = data.getUUID(ref.subLevelKey());
            else if(data.get(ref.subLevelKey()) instanceof StringTag val){
                id = UUID.fromString(val.getAsString()); stringId = true;
            }
        }
        CompoundTag endpoint = SchematicConnectionData.reference(id, BlockPos.containing(point), frames);
        if(ref.positionKey().isEmpty()){
            for(String key : List.of("x", "y", "z", "X", "Y", "Z")) data.remove(key);
        }else data.remove(ref.positionKey());
        if(ref.subLevelKey() != null) data.remove(ref.subLevelKey());
        if(endpoint == null){ data.putBoolean("gadgetsngizmos:missing_reference", true); return; }
        BlockPos floor = BlockPos.containing(point);
        endpoint.putDouble("Dx", point.x - floor.getX()); endpoint.putDouble("Dy", point.y - floor.getY());
        endpoint.putDouble("Dz", point.z - floor.getZ());
        endpoint.put("Encoding", raw.copy());
        endpoint.putBoolean("StringId", stringId);
        data.put(REF + ref.positionKey(), endpoint);
    }

    // Restore endpoint coordinates once and keep missing external links unavailable
    private static void place(CompoundTag data, Reference ref, BlockPos owner, SubLevelSchematicSerializationContext ctx){
        String key = REF + ref.positionKey();
        if(!data.contains(key, Tag.TAG_COMPOUND)) return;
        CompoundTag endpoint = data.getCompound(key);
        if(ref.format() == PositionFormat.BODY){
            var mapping = endpoint.hasUUID("Body") ? ctx.getMapping(endpoint.getUUID("Body")) : null;
            data.remove(key);
            if(mapping == null) return;
            if(endpoint.getBoolean("StringId")) data.putString(ref.positionKey(), mapping.newUUID().toString());
            else data.putUUID(ref.positionKey(), mapping.newUUID());
            return;
        }
        BlockPos mapped = SchematicConnectionData.resolve(endpoint, ctx);
        data.remove(key);
        if(mapped == null) return;
        Vec3 point = Vec3.atLowerCornerOf(mapped).add(endpoint.getDouble("Dx"), endpoint.getDouble("Dy"), endpoint.getDouble("Dz"));
        if(ref.format() == PositionFormat.RELATIVE_VECTOR) point = point.subtract(Vec3.atLowerCornerOf(owner));
        Tag encoded = encode(point, ref.format(), endpoint.get("Encoding"));
        if(ref.positionKey().isEmpty() && encoded instanceof CompoundTag val) val.getAllKeys().forEach(field -> data.put(field, val.get(field).copy()));
        else data.put(ref.positionKey(), encoded);
        if(ref.subLevelKey() != null){
            UUID id = ctx.getMapping(endpoint.getUUID("Body")).newUUID();
            if(endpoint.getBoolean("StringId")) data.putString(ref.subLevelKey(), id.toString());
            else data.putUUID(ref.subLevelKey(), id);
        }
    }

    // Decode the position format declared by the integration
    private static Vec3 position(Tag raw, PositionFormat format){
        if(raw == null) return null;
        if(format == PositionFormat.LONG && raw instanceof LongTag val) return Vec3.atLowerCornerOf(BlockPos.of(val.getAsLong()));
        if(raw instanceof IntArrayTag val){
            int[] xyz = val.getAsIntArray();
            return xyz.length == 3 ? new Vec3(xyz[0], xyz[1], xyz[2]) : null;
        }
        if(raw instanceof CompoundTag val){
            if(val.contains("V", Tag.TAG_LIST)) return position(val.get("V"), PositionFormat.VECTOR);
            String x = val.contains("X") ? "X" : "x", y = val.contains("Y") ? "Y" : "y", z = val.contains("Z") ? "Z" : "z";
            return val.contains(x) && val.contains(y) && val.contains(z) ? new Vec3(val.getDouble(x), val.getDouble(y), val.getDouble(z)) : null;
        }
        if(raw instanceof ListTag val && val.size() == 3) return new Vec3(val.getDouble(0), val.getDouble(1), val.getDouble(2));
        return null;
    }

    // Preserve the original coordinate encoding expected by the block's reader
    private static Tag encode(Vec3 point, PositionFormat format, Tag original){
        BlockPos pos = BlockPos.containing(point);
        if(format == PositionFormat.LONG) return LongTag.valueOf(pos.asLong());
        if(original instanceof IntArrayTag) return new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()});
        if(original instanceof ListTag){
            ListTag list = new ListTag();
            list.add(DoubleTag.valueOf(point.x)); list.add(DoubleTag.valueOf(point.y)); list.add(DoubleTag.valueOf(point.z));
            return list;
        }
        if(original instanceof CompoundTag val && val.contains("V", Tag.TAG_LIST)){
            CompoundTag tag = new CompoundTag();
            tag.put("V", encode(point, PositionFormat.VECTOR, val.get("V")));
            return tag;
        }
        if(format == PositionFormat.BLOCK_POS) return NbtUtils.writeBlockPos(pos);
        CompoundTag tag = new CompoundTag();
        boolean upper = original instanceof CompoundTag val && val.contains("X");
        tag.putDouble(upper ? "X" : "x", point.x); tag.putDouble(upper ? "Y" : "y", point.y); tag.putDouble(upper ? "Z" : "z", point.z);
        return tag;
    }

    // Visit compounds through explicitly declared nested fields and compound lists
    private static void walk(CompoundTag data, List<String> path, Consumer<CompoundTag> action){
        if(path.isEmpty()){ action.accept(data); return; }
        Tag child = data.get(path.getFirst());
        List<String> tail = path.subList(1, path.size());
        if(child instanceof CompoundTag tag) walk(tag, tail, action);
        else if(child instanceof ListTag list) for(Tag raw : list) if(raw instanceof CompoundTag tag) walk(tag, tail, action);
    }

    // Transform a declared list of world-space points without changing its codec shape
    private static void points(CompoundTag data, List<String> path, java.util.function.UnaryOperator<Vec3> transform){
        if(path.isEmpty()) return;
        walk(data, path.subList(0, path.size() - 1), tag -> {
            String key = path.getLast();
            if(!(tag.get(key) instanceof ListTag list)) return;
            for(int idx = 0; idx < list.size(); idx++){
                Tag raw = list.get(idx);
                Vec3 point = position(raw, PositionFormat.VECTOR);
                if(point != null) list.set(idx, encode(transform.apply(point), PositionFormat.VECTOR, raw));
            }
        });
    }

    // Replace declared runtime identities consistently across all paired endpoints
    private static void identities(CompoundTag data, Set<String> keys, java.util.function.UnaryOperator<UUID> transform){
        for(String key : List.copyOf(data.getAllKeys())){
            Tag raw = data.get(key);
            if(keys.contains(key)){
                if(data.hasUUID(key)) data.putUUID(key, transform.apply(data.getUUID(key)));
                else if(raw instanceof StringTag){
                    try{ data.putString(key, transform.apply(UUID.fromString(raw.getAsString())).toString()); }
                    catch(IllegalArgumentException err){ throw new IllegalArgumentException("Invalid schematic link identity: " + key, err); }
                }
            }else if(raw instanceof CompoundTag tag) identities(tag, keys, transform);
            else if(raw instanceof ListTag list) for(Tag val : list) if(val instanceof CompoundTag tag) identities(tag, keys, transform);
        }
    }

    // Derive fresh identities from this export or placement's complete body roster
    private static UUID identity(UUID id, Collection<UUID> bodies){
        String roster = bodies.stream().distinct().sorted(java.util.Comparator.comparing(UUID::toString))
                .map(UUID::toString).collect(java.util.stream.Collectors.joining(":"));
        return UUID.nameUUIDFromBytes((id + ":" + roster).getBytes(StandardCharsets.UTF_8));
    }

    // Remove incomplete nested connections before upstream readers receive their codec data
    private static void pruneUnavailable(CompoundTag data){
        for(String key : List.copyOf(data.getAllKeys())){
            Tag raw = data.get(key);
            if(raw instanceof ListTag list){
                for(int idx = list.size() - 1; idx >= 0; idx--) if(missing(list.get(idx))) list.remove(idx);
            }else if(raw instanceof CompoundTag tag && missing(tag)) data.remove(key);
        }
        data.remove("gadgetsngizmos:missing_reference");
    }

    // Detect an unavailable endpoint anywhere inside one declared connection
    private static boolean missing(Tag raw){
        if(raw instanceof CompoundTag tag){
            if(tag.getBoolean("gadgetsngizmos:missing_reference")) return true;
            return tag.getAllKeys().stream().anyMatch(key -> missing(tag.get(key)));
        }
        if(raw instanceof ListTag list) for(Tag val : list) if(missing(val)) return true;
        return false;
    }

    // Overlay one declared nested field without copying unrelated persistent state
    private static void copyField(CompoundTag src, CompoundTag destination, List<String> path){
        if(path.isEmpty()) return;
        CompoundTag from = src, to = destination;
        for(int idx = 0; idx < path.size() - 1; idx++){
            String key = path.get(idx);
            from = from.getCompound(key);
            if(!to.contains(key, Tag.TAG_COMPOUND)) to.put(key, new CompoundTag());
            to = to.getCompound(key);
        }
        String key = path.getLast();
        to.remove(key);
        Tag val = from.get(key);
        if(val != null) to.put(key, val.copy());
    }
}
