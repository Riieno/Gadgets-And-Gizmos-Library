package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.util.SableNBTUtils;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

// Read and write bounded Create and Sable structure files in the server's schematics directory
public final class SubLevelSchematicFiles{
    private SubLevelSchematicFiles(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep integrated shared files in the world folder so host client files remain private
    public static Path directory(MinecraftServer server) throws IOException{
        Path root = server.isDedicatedServer() ? server.getServerDirectory() : server.getWorldPath(LevelResource.ROOT);
        Path dir = root.resolve("schematics").toAbsolutePath().normalize();
        if(Files.isSymbolicLink(dir)) throw new IOException("The schematics directory must not be a symbolic link");
        Files.createDirectories(dir);
        return dir;
    }

    // List shared server files without reading their block data
    public static List<Entry> catalogue(MinecraftServer server) throws IOException{
        return catalogue(directory(server), "");
    }

    // Scan nested folders and keep client file identities separate from shared server files
    public static List<Entry> catalogue(Path directory, String idPrefix) throws IOException{
        Path root = directory.toAbsolutePath().normalize();
        if(Files.isSymbolicLink(root)) throw new IOException("Schematic directories must not be symbolic links");
        if(!Files.isDirectory(root)) return List.of();
        try(var paths = Files.walk(root, 5)){
            return paths.filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path)
                            && supportedName(path.getFileName().toString()))
                    .sorted().map(path -> {
                        String name = root.relativize(path).toString().replace('\\', '/');
                        return new Entry(UUID.nameUUIDFromBytes((idPrefix + name).getBytes(StandardCharsets.UTF_8)), name);
                    }).toList();
        }
    }

    // Recognize structure NBT and Toolgun archives regardless of extension casing
    public static boolean supportedName(String name){
        String val = name.toLowerCase(Locale.ROOT);
        return val.endsWith(".nbt") || val.endsWith(".excraft");
    }

    // Resolve catalogue identities again and reject files outside the selected folder
    public static byte[] read(Path directory, String idPrefix, UUID id) throws IOException{
        Entry entry = catalogue(directory, idPrefix).stream().filter(row -> row.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Schematic file was removed or renamed; refresh the list"));
        return read(directory, idPrefix, entry);
    }

    // Read the selected catalogue entry without rescanning or depending on its list position
    public static byte[] read(Path directory, String idPrefix, Entry entry) throws IOException{
        Path root = directory.toAbsolutePath().normalize();
        if(entry == null || !supportedName(entry.name()) || !entry.id().equals(
                UUID.nameUUIDFromBytes((idPrefix + entry.name()).getBytes(StandardCharsets.UTF_8))))
            throw new IllegalArgumentException("Invalid schematic catalogue entry");
        if(Files.isSymbolicLink(root)) throw new IOException("Schematic directories must not be symbolic links");
        Path file = root.resolve(entry.name()).normalize();
        if(!file.startsWith(root) || !Files.isRegularFile(file) || Files.isSymbolicLink(file) || !file.toRealPath().startsWith(root.toRealPath()))
            throw new IOException("Schematic file is outside its directory");
        if(Files.size(file) > SchematicClientFiles.MAXIMUM_BYTES) throw new IOException("Schematic file exceeds the size limit");
        byte[] res = Files.readAllBytes(file);
        if(res.length > SchematicClientFiles.MAXIMUM_BYTES) throw new IOException("Schematic file exceeds the size limit");
        return res;
    }

    // Decode compressed or raw NBT with the same limits for local files and client uploads
    public static SubLevelSchematic decode(HolderGetter<Block> registry, byte[] data, int maximumBodies, int maximumBlocks) throws IOException{
        if(data == null || data.length == 0 || data.length > SchematicClientFiles.MAXIMUM_BYTES)
            throw new IOException("Schematic file is empty or exceeds the size limit");
        try(var input = new ByteArrayInputStream(data)){
            var budget = NbtAccounter.create(256L * 1024 * 1024);
            CompoundTag tag = data.length >= 2 && (data[0] & 255) == 31 && (data[1] & 255) == 139
                    ? NbtIo.readCompressed(input, budget) : NbtIo.read(new DataInputStream(input), budget);
            if(tag == null) throw new IOException("Schematic NBT is empty");
            return decode(registry, tag, maximumBodies, maximumBlocks);
        }catch(RuntimeException err){ throw new IOException("Cannot read schematic: " + err.getMessage(), err); }
    }

    // Resolve an opaque catalogue ID instead of accepting a client-provided path
    public static SubLevelSchematic load(MinecraftServer server, UUID id, int maximumBodies, int maximumBlocks) throws IOException{
        return load(server.overworld(), id, maximumBodies, maximumBlocks);
    }

    // Prepare safe configuration using the destination world's registry and block callbacks
    public static SubLevelSchematic load(ServerLevel level, UUID id, int maximumBodies, int maximumBlocks) throws IOException{
        MinecraftServer server = level.getServer();
        if(!server.isSameThread()) throw new IllegalStateException("Schematic loading requires the server thread");
        return sanitize(level, decode(server.registryAccess().lookupOrThrow(Registries.BLOCK),
                read(directory(server), "", id), maximumBodies, maximumBlocks));
    }

    // Export an independent template without replacing an existing schematic
    public static String save(MinecraftServer server, String name, SubLevelSchematic schematic) throws IOException{
        if(name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Use 1-64 letters, numbers, underscores or dashes for the schematic name");
        Path dir = directory(server);
        String next = name;
        for(int idx = 2; Files.exists(dir.resolve(next + ".nbt")); idx++) next = name + "-" + idx;
        Path target = dir.resolve(next + ".nbt");
        Path temp = Files.createTempFile(dir, "schematic-", ".tmp");
        try{
            NbtIo.writeCompressed(encode(schematic), temp);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        }finally{ Files.deleteIfExists(temp); }
        return target.getFileName().toString();
    }

    // Decode plain structures and connected Sable templates with strict limits
    public static SubLevelSchematic decode(HolderGetter<Block> registry, CompoundTag tag, int maximumBodies, int maximumBlocks){
        if(maximumBodies < 1 || maximumBlocks < 1) throw new IllegalArgumentException("Schematic limits must be positive");
        tag = SubLevelSchematicFormats.normalize(registry, tag, maximumBodies, maximumBlocks);
        List<SubLevelSchematic.Body> bodies = new ArrayList<>();
        if(!tag.getList("blocks", Tag.TAG_COMPOUND).isEmpty()) bodies.add(decodeBody(registry, tag, UUID.randomUUID(), Vec3.ZERO, new Quaterniond(), maximumBlocks));
        ListTag children = tag.getList("sub_levels", Tag.TAG_COMPOUND);
        if(children.size() + bodies.size() > maximumBodies) throw new IllegalArgumentException("Schematic exceeds the body limit");
        for(int idx = 0; idx < children.size(); idx++){
            CompoundTag child = children.getCompound(idx);
            if(!child.getList("sub_levels", Tag.TAG_COMPOUND).isEmpty()) throw new IllegalArgumentException("Nested sublevel templates are unsupported");
            var pos = SableNBTUtils.readVector3d(child.getCompound("position"));
            Quaterniond turn = SableNBTUtils.readQuaternion(child.getCompound("orientation"));
            if(!Double.isFinite(pos.x + pos.y + pos.z) || !Double.isFinite(turn.lengthSquared())
                    || Math.abs(turn.lengthSquared() - 1) > 0.01) throw new IllegalArgumentException("Invalid schematic body pose");
            bodies.add(decodeBody(registry, child, child.getUUID("uuid"), new Vec3(pos.x, pos.y, pos.z), turn.normalize(), maximumBlocks));
        }
        if(!tag.getList("entities", Tag.TAG_COMPOUND).isEmpty()) throw new IllegalArgumentException("Use a block-only schematic; entity templates are unsupported");
        var ids = new HashSet<UUID>();
        for(var body : bodies) if(!ids.add(body.id())) throw new IllegalArgumentException("Duplicate schematic body IDs");
        var available = new HashSet<UUID>();
        for(var body : bodies) if(!body.blocks().isEmpty()) available.add(body.id());
        ListTag savedJoints = tag.getList("gadgetsngizmos:joints", Tag.TAG_COMPOUND);
        if(savedJoints.size() > 1024) throw new IllegalArgumentException("Schematic exceeds the weld limit");
        List<SubLevelSchematic.Joint> joints = new ArrayList<>();
        for(int idx = 0; idx < savedJoints.size(); idx++){
            var joint = readJoint(savedJoints.getCompound(idx));
            if(!ids.contains(joint.first()) || !ids.contains(joint.second()))
                throw new IllegalArgumentException("Schematic weld refers to an unknown body");
            for(Vec3 anchor : List.of(joint.firstAnchor(), joint.secondAnchor()))
                if(Math.max(Math.abs(anchor.x), Math.max(Math.abs(anchor.y), Math.abs(anchor.z))) > 1_000_000)
                    throw new IllegalArgumentException("Schematic weld anchor exceeds the limit");
            if(available.contains(joint.first()) && available.contains(joint.second())) joints.add(joint);
        }
        SubLevelSchematic res = new SubLevelSchematic(bodies.stream().filter(body -> !body.blocks().isEmpty()).toList(), joints);
        if(res.bodies().isEmpty()) throw new IllegalArgumentException("Schematic contains no available blocks");
        if(res.blockCount() > maximumBlocks) throw new IllegalArgumentException("Schematic exceeds the block limit");
        return res;
    }

    // Encode the native sub_levels extension while keeping the root structure empty
    public static CompoundTag encode(SubLevelSchematic schematic){
        CompoundTag res = structureTag(new BlockPos(1, 1, 1));
        ListTag children = new ListTag();
        for(var body : schematic.bodies()){
            CompoundTag tag = structureTag(body.size());
            tag.putUUID("uuid", body.id());
            tag.put("position", SableNBTUtils.writeVector3d(new org.joml.Vector3d(body.corner().x, body.corner().y, body.corner().z)));
            tag.put("orientation", SableNBTUtils.writeQuaternion(body.orientation()));
            if(body.importFrame().format() != SubLevelSchematic.Format.NATIVE){
                CompoundTag frame = new CompoundTag();
                frame.putString("Format", body.importFrame().format().name()); frame.putUUID("Original", body.importFrame().originalId());
                frame.put("Origin", coordinates(body.importFrame().origin())); frame.putInt("BlueprintId", body.importFrame().blueprintId());
                tag.put("gadgetsngizmos:import", frame);
            }
            Map<net.minecraft.world.level.block.state.BlockState, Integer> palette = new LinkedHashMap<>();
            ListTag blocks = new ListTag();
            for(var block : body.blocks()){
                CompoundTag row = new CompoundTag();
                row.put("pos", coordinates(block.pos()));
                row.putInt("state", palette.computeIfAbsent(block.state(), val -> palette.size()));
                if(!block.data().isEmpty()) row.put("nbt", block.data());
                blocks.add(row);
            }
            ListTag states = new ListTag();
            palette.keySet().forEach(state -> states.add(NbtUtils.writeBlockState(state)));
            tag.put("palette", states); tag.put("blocks", blocks);
            children.add(tag);
        }
        res.put("sub_levels", children);
        ListTag joints = new ListTag();
        schematic.joints().forEach(joint -> joints.add(writeJoint(joint)));
        res.put("gadgetsngizmos:joints", joints);
        return res;
    }

    // Remove stored inventories and unsafe NBT using Create's schematic policy before material checks
    public static SubLevelSchematic sanitize(ServerLevel level, SubLevelSchematic schematic){
        var ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.SAVE, null);
        for(var body : schematic.bodies()){
            var mapping = new SubLevelSchematicSerializationContext.SchematicMapping(
                    new org.joml.Vector3d(body.corner().x, body.corner().y, body.corner().z), body.orientation(), body.id(),
                    pos -> ((BlockPos)pos).subtract(body.importFrame().origin()));
            ctx.getMappings().put(body.id(), mapping);
            ctx.getMappings().put(body.importFrame().originalId(), mapping);
        }
        var prev = SubLevelSchematicSerializationContext.getCurrentContext();
        List<SubLevelSchematic.Body> bodies = new ArrayList<>();
        try{
            for(var body : schematic.bodies()){
                List<SubLevelSchematic.Block> blocks = new ArrayList<>();
                for(var block : body.blocks()){
                    SubLevelSchematicSerializationContext.setCurrentContext(null);
                    CompoundTag data = SchematicImportAdapters.prepare(schematic, body, block);
                    var be = SchematicBlockData.create(level, block.pos().offset(body.importFrame().origin()), block.state(), data);
                    SubLevelSchematicSerializationContext.setCurrentContext(ctx);
                    blocks.add(new SubLevelSchematic.Block(block.pos(), block.state(), SchematicBlockData.safe(level, block.state(), be)));
                }
                bodies.add(new SubLevelSchematic.Body(body.id(), body.corner(), body.orientation(), body.size(), blocks));
            }
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(prev); }
        return new SubLevelSchematic(bodies, schematic.joints());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read one retained attachment frame
    static SubLevelSchematic.Joint readJoint(CompoundTag tag){
        return new SubLevelSchematic.Joint(tag.getUUID("First"), tag.getUUID("Second"),
                SubLevelSchematic.JointType.valueOf(tag.getString("Type")), readVector(tag, "FirstAnchor"), readVector(tag, "SecondAnchor"),
                SableNBTUtils.readQuaternion(tag.getCompound("Orientation")), readVector(tag, "FirstAxis"), readVector(tag, "SecondAxis"));
    }

    // Preserve native weld frames in both schematic files and saved sublevel metadata
    static CompoundTag writeJoint(SubLevelSchematic.Joint joint){
        CompoundTag tag = new CompoundTag();
        tag.putUUID("First", joint.first()); tag.putUUID("Second", joint.second()); tag.putString("Type", joint.type().name());
        tag.put("FirstAnchor", SableNBTUtils.writeVector3d(vector(joint.firstAnchor())));
        tag.put("SecondAnchor", SableNBTUtils.writeVector3d(vector(joint.secondAnchor())));
        tag.put("Orientation", SableNBTUtils.writeQuaternion(joint.orientation()));
        tag.put("FirstAxis", SableNBTUtils.writeVector3d(vector(joint.firstAxis())));
        tag.put("SecondAxis", SableNBTUtils.writeVector3d(vector(joint.secondAxis())));
        return tag;
    }

    private static Vec3 readVector(CompoundTag tag, String key){
        var val = SableNBTUtils.readVector3d(tag.getCompound(key));
        return new Vec3(val.x, val.y, val.z);
    }

    private static org.joml.Vector3d vector(Vec3 val){ return new org.joml.Vector3d(val.x, val.y, val.z); }

    // Reject malformed block lists before allocating any plots
    private static SubLevelSchematic.Body decodeBody(HolderGetter<Block> registry, CompoundTag tag, UUID id, Vec3 corner, Quaterniond turn, int maximumBlocks){
        if(!tag.getList("entities", Tag.TAG_COMPOUND).isEmpty()) throw new IllegalArgumentException("Use a block-only schematic; entity templates are unsupported");
        BlockPos size = readCoordinates(tag.getList("size", Tag.TAG_INT));
        if(size.getX() < 1 || size.getY() < 1 || size.getZ() < 1 || size.getX() > 512 || size.getY() > 512 || size.getZ() > 512) throw new IllegalArgumentException("Schematic dimensions exceed the limit");
        ListTag states = tag.getList("palette", Tag.TAG_COMPOUND);
        if(states.isEmpty()){
            ListTag palettes = tag.getList("palettes", Tag.TAG_LIST);
            if(!palettes.isEmpty()) states = (ListTag) palettes.get(0);
        }
        List<net.minecraft.world.level.block.state.BlockState> palette = new ArrayList<>();
        for(int idx = 0; idx < states.size(); idx++){
            CompoundTag row = states.getCompound(idx);
            var state = NbtUtils.readBlockState(registry, row);
            // Keep palette indexes stable while unavailable registrations resolve to air
            palette.add(state);
        }
        ListTag rows = tag.getList("blocks", Tag.TAG_COMPOUND);
        if(rows.size() > maximumBlocks) throw new IllegalArgumentException("Schematic exceeds the block limit");
        List<SubLevelSchematic.Block> blocks = new ArrayList<>();
        var seen = new HashSet<BlockPos>();
        for(int idx = 0; idx < rows.size(); idx++){
            CompoundTag row = rows.getCompound(idx);
            BlockPos pos = readCoordinates(row.getList("pos", Tag.TAG_INT));
            int stateIdx = row.getInt("state");
            if(pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0 || pos.getX() >= size.getX()
                    || pos.getY() >= size.getY() || pos.getZ() >= size.getZ() || !seen.add(pos)
                    || stateIdx < 0 || stateIdx >= palette.size()) throw new IllegalArgumentException("Invalid schematic block entry");
            var state = palette.get(stateIdx);
            if(state.isAir() || state.is(Blocks.STRUCTURE_VOID)) continue;
            blocks.add(new SubLevelSchematic.Block(pos, state, row.getCompound("nbt")));
        }
        SubLevelSchematic.ImportFrame frame = null;
        if(tag.contains("gadgetsngizmos:import", Tag.TAG_COMPOUND)){
            CompoundTag src = tag.getCompound("gadgetsngizmos:import");
            BlockPos origin = readCoordinates(src.getList("Origin", Tag.TAG_INT));
            if(Math.abs((long) origin.getX()) > 1_000_000 || Math.abs((long) origin.getY()) > 16384 || Math.abs((long) origin.getZ()) > 1_000_000)
                throw new IllegalArgumentException("Invalid schematic import origin");
            frame = new SubLevelSchematic.ImportFrame(SubLevelSchematic.Format.valueOf(src.getString("Format")),
                    src.getUUID("Original"), origin, src.getInt("BlueprintId"));
        }
        return new SubLevelSchematic.Body(id, corner, turn, size, blocks, frame);
    }

    // Create vanilla structure metadata for a block-only template
    private static CompoundTag structureTag(BlockPos size){
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        tag.put("size", coordinates(size)); tag.put("palette", new ListTag());
        tag.put("blocks", new ListTag()); tag.put("entities", new ListTag());
        return tag;
    }

    // Write vanilla structure coordinates
    private static ListTag coordinates(BlockPos pos){
        ListTag res = new ListTag();
        res.add(IntTag.valueOf(pos.getX())); res.add(IntTag.valueOf(pos.getY())); res.add(IntTag.valueOf(pos.getZ()));
        return res;
    }

    // Require all three structure coordinates
    private static BlockPos readCoordinates(ListTag tag){
        if(tag.size() != 3) throw new IllegalArgumentException("Invalid schematic coordinates");
        return new BlockPos(tag.getInt(0), tag.getInt(1), tag.getInt(2));
    }

    // Expose an opaque file ID and its display name
    public record Entry(UUID id, String name){}
}
