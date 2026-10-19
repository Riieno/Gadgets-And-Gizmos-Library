package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Normalize foreign block blueprints before the native decoder validates their geometry
final class SubLevelSchematicFormats{
    private SubLevelSchematicFormats(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Identify formats by their contents rather than their file extension
    static CompoundTag normalize(HolderGetter<Block> registry, CompoundTag tag, int maximumBodies, int maximumBlocks){
        if(tag.contains("format")){
            String format = tag.getString("format");
            if(!format.equals("enxv_aeronautics_plot_print_v8") && !format.equals("enxv_aeronautics_plot_print_v9"))
                throw new IllegalArgumentException("Unsupported Toolgun schematic format: " + format);
            return toolgun(registry, tag, maximumBodies, maximumBlocks);
        }
        ListTag bodies = tag.getList("sub_levels", Tag.TAG_COMPOUND);
        if(!bodies.isEmpty() && bodies.getCompound(0).contains("block_palette"))
            return photomancy(tag, maximumBodies, maximumBlocks);
        return tag;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Convert Photomancy v1 body palettes, block entity indexes and anchor poses
    private static CompoundTag photomancy(CompoundTag root, int maximumBodies, int maximumBlocks){
        if(root.getInt("version") != 1) throw new IllegalArgumentException("Unsupported Photomancy blueprint version");
        ListTag saved = root.getList("sub_levels", Tag.TAG_COMPOUND);
        requireBodies(saved, maximumBodies);
        ListTag bodies = new ListTag();
        int count = 0;
        for(int idx = 0; idx < saved.size(); idx++){
            CompoundTag src = saved.getCompound(idx);
            requireBlockOnly(src);
            ListTag palette = src.getList("block_palette", Tag.TAG_COMPOUND).copy();
            // Replace explicitly unavailable entries without changing saved palette indexes
            for(int stateIdx : src.getIntArray("unavailable_palette_ids")){
                if(stateIdx < 0 || stateIdx >= palette.size()) throw new IllegalArgumentException("Invalid Photomancy unavailable palette index");
                palette.set(stateIdx, NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
            }
            CompoundTag bounds = src.getCompound("local_bounds");
            BlockPos size = new BlockPos(bounds.getInt("max_x") - bounds.getInt("min_x") + 1,
                    bounds.getInt("max_y") - bounds.getInt("min_y") + 1, bounds.getInt("max_z") - bounds.getInt("min_z") + 1);
            ListTag blocks = src.getList("blocks", Tag.TAG_COMPOUND);
            count = checkedCount(count, blocks.size(), maximumBlocks);
            CompoundTag pose = src.getCompound("relative_pose");
            Quaterniond turn = quaternion(pose, "orientation", false);
            Vector3d corner = vector(pose, "position");
            Vector3d offset = new Vector3d(-size.getX() * 0.5, -size.getY() * 0.5, -size.getZ() * 0.5);
            if(src.contains("anchor_bounds", Tag.TAG_COMPOUND)){
                CompoundTag anchor = src.getCompound("anchor_bounds");
                offset.set(anchor.getDouble("minX"), anchor.getDouble("minY"), anchor.getDouble("minZ"));
            }
            corner.add(turn.transform(offset));
            ListTag entities = src.getList("block_entities", Tag.TAG_COMPOUND);
            ListTag rows = new ListTag();
            for(int blockIdx = 0; blockIdx < blocks.size(); blockIdx++){
                CompoundTag block = blocks.getCompound(blockIdx);
                CompoundTag pos = block.getCompound("local_pos");
                BlockPos local = integerPosition(pos);
                if(!block.contains("palette_id", Tag.TAG_INT)) throw new IllegalArgumentException("Photomancy block has no palette index");
                CompoundTag row = row(local, block.getInt("palette_id"));
                if(block.contains("block_entity_data_id")){
                    int entityIdx = block.getInt("block_entity_data_id");
                    if(entityIdx < 0 || entityIdx >= entities.size()) throw new IllegalArgumentException("Invalid Photomancy block entity index");
                    row.put("nbt", entities.getCompound(entityIdx).copy());
                }
                rows.add(row);
            }
            CompoundTag body = body(src.getUUID("source_uuid"), corner, turn, size, palette, rows);
            body.put("gadgetsngizmos:import", frame("PHOTOMANCY", src.getUUID("source_uuid"), BlockPos.ZERO, src.getInt("id")));
            bodies.add(body);
        }
        return assembly(bodies);
    }

    // Convert Toolgun v8/v9 native plot sections using their saved vertical layout
    private static CompoundTag toolgun(HolderGetter<Block> registry, CompoundTag root, int maximumBodies, int maximumBlocks){
        ListTag saved = root.getList("sublevels", Tag.TAG_COMPOUND);
        requireBodies(saved, maximumBodies);
        UUID rootId = root.getUUID("root_sublevel");
        double anchorY = Double.NaN;
        for(int idx = 0; idx < saved.size(); idx++){
            CompoundTag src = saved.getCompound(idx);
            if(src.hasUUID("sublevel_id") && src.getUUID("sublevel_id").equals(rootId)) anchorY = vector(src, "relative_position").y;
        }
        if(!Double.isFinite(anchorY)) throw new IllegalArgumentException("Toolgun root body is missing");
        int minHeight = root.contains("source_min_build_height", Tag.TAG_INT) ? root.getInt("source_min_build_height") : -64;
        if(minHeight < -4096 || minHeight > 4096 || minHeight % 16 != 0)
            throw new IllegalArgumentException("Invalid Toolgun source height");
        Quaterniond rootTurn = quaternion(root, "root_orientation", true);
        Vector3d rootOffset = root.contains("root_rotation_offset") ? vector(root, "root_rotation_offset") : new Vector3d();
        rootTurn.transform(rootOffset);
        Pose3d rootPose = new Pose3d(new Vector3d(), rootTurn, rootOffset, new Vector3d(1));
        ListTag bodies = new ListTag();
        int count = 0;
        for(int idx = 0; idx < saved.size(); idx++){
            CompoundTag src = saved.getCompound(idx);
            requireBlockOnly(src);
            CompoundTag plot = src.getCompound("plot");
            List<PlotBlock> blocks = plot(registry, plot, minHeight, maximumBlocks - count);
            count = checkedCount(count, blocks.size(), maximumBlocks);
            // Retain empty source identities until the decoder removes their attachment frames
            if(blocks.isEmpty()){
                bodies.add(body(src.getUUID("sublevel_id"), new Vector3d(), new Quaterniond(), new BlockPos(1, 1, 1), new ListTag(), new ListTag()));
                continue;
            }
            BlockPos min = minimum(blocks);
            BlockPos max = maximum(blocks);
            BlockPos size = max.subtract(min).offset(1, 1, 1);
            Vector3d anchor = plotCenter(plot, anchorY);
            if(src.contains("local_anchor")){
                Vector3d explicit = vector(src, "local_anchor");
                String space = src.getString("local_anchor_space");
                if(space.equals("saved_plot_local_v1")) anchor = explicit;
                else if(space.isBlank()) anchor.y = explicit.y;
                else throw new IllegalArgumentException("Unsupported Toolgun anchor space: " + space);
            }
            Vector3d corner = rootPose.transformPosition(vector(src, "relative_position"));
            Quaterniond turn = new Quaterniond(rootTurn).mul(quaternion(src, "relative_orientation", true)).normalize();
            corner.add(turn.transform(new Vector3d(min.getX(), min.getY(), min.getZ()).sub(anchor)));
            ListTag palette = new ListTag();
            Map<CompoundTag, Integer> states = new HashMap<>();
            ListTag rows = new ListTag();
            for(PlotBlock block : blocks){
                int stateIdx = states.computeIfAbsent(block.state(), val -> { palette.add(val.copy()); return palette.size() - 1; });
                CompoundTag row = row(block.pos().subtract(min), stateIdx);
                if(block.data() != null) row.put("nbt", block.data().copy());
                rows.add(row);
            }
            CompoundTag body = body(src.getUUID("sublevel_id"), corner, turn, size, palette, rows);
            body.put("gadgetsngizmos:import", frame("TOOLGUN",
                    src.hasUUID("original_sublevel_id") ? src.getUUID("original_sublevel_id") : src.getUUID("sublevel_id"), min, -1));
            bodies.add(body);
        }
        CompoundTag res = assembly(bodies);
        ListTag constraints = root.getList("toolgun_constraints", Tag.TAG_COMPOUND);
        if(constraints.size() > 1024) throw new IllegalArgumentException("Schematic exceeds the weld limit");
        ListTag joints = new ListTag();
        for(int idx = 0; idx < constraints.size(); idx++){
            CompoundTag src = constraints.getCompound(idx);
            String space = src.getString("constraint_space");
            if(!space.equals("saved_plot_local_v1")) throw new IllegalArgumentException("Unsupported legacy Toolgun weld coordinates");
            UUID first = src.getUUID("first_sublevel"), second = src.getUUID("second_sublevel");
            CompoundTag firstBody = findBody(bodies, first), secondBody = findBody(bodies, second);
            if(firstBody.getList("blocks", Tag.TAG_COMPOUND).isEmpty() || secondBody.getList("blocks", Tag.TAG_COMPOUND).isEmpty()) continue;
            Vector3d firstMin = importOrigin(firstBody), secondMin = importOrigin(secondBody);
            CompoundTag joint = new CompoundTag();
            joint.putUUID("First", first); joint.putUUID("Second", second); joint.putString("Type", src.getString("mode"));
            joint.put("FirstAnchor", SableNBTUtils.writeVector3d(vector(src, "first_local").sub(firstMin)));
            joint.put("SecondAnchor", SableNBTUtils.writeVector3d(vector(src, "second_local").sub(secondMin)));
            joint.put("Orientation", SableNBTUtils.writeQuaternion(quaternion(src, "relative_orientation", true)));
            if(src.getString("mode").equals("BEARING")){
                joint.put("FirstAxis", SableNBTUtils.writeVector3d(vector(src, "first_axis_local")));
                joint.put("SecondAxis", SableNBTUtils.writeVector3d(vector(src, "second_axis_local")));
            }
            joints.add(joint);
        }
        res.put("gadgetsngizmos:joints", joints);
        return res;
    }

    // Resolve only attachment endpoints belonging to this imported assembly
    private static CompoundTag findBody(ListTag bodies, UUID id){
        for(int idx = 0; idx < bodies.size(); idx++) if(bodies.getCompound(idx).getUUID("uuid").equals(id)) return bodies.getCompound(idx);
        throw new IllegalArgumentException("Toolgun weld refers to a missing body");
    }

    // Recover the compacted plot origin used by a normalized body
    private static Vector3d importOrigin(CompoundTag body){
        ListTag pos = body.getCompound("gadgetsngizmos:import").getList("Origin", Tag.TAG_INT);
        return new Vector3d(pos.getInt(0), pos.getInt(1), pos.getInt(2));
    }

    // Read palette-packed sections without depending on the Toolgun implementation
    private static List<PlotBlock> plot(HolderGetter<Block> registry, CompoundTag plot, int minHeight, int maximumBlocks){
        requireBlockOnly(plot);
        CompoundTag chunks = plot.getCompound("chunks");
        List<PlotBlock> res = new ArrayList<>();
        if(chunks.size() > 4096) throw new IllegalArgumentException("Toolgun plot exceeds the chunk limit");
        for(String key : chunks.getAllKeys()){
            ChunkPos pos = new ChunkPos(Long.parseLong(key));
            if(Math.abs((long) pos.x) > 32768 || Math.abs((long) pos.z) > 32768)
                throw new IllegalArgumentException("Toolgun plot chunk is outside its local range");
            CompoundTag chunk = chunks.getCompound(key);
            requireBlockOnly(chunk);
            Map<BlockPos, CompoundTag> entities = new HashMap<>();
            ListTag blockEntities = chunk.getList("block_entities", Tag.TAG_COMPOUND);
            for(int idx = 0; idx < blockEntities.size(); idx++){
                CompoundTag row = blockEntities.getCompound(idx);
                BlockPos local = integerPosition(row);
                if(entities.put(local, row) != null) throw new IllegalArgumentException("Duplicate Toolgun block entity");
            }
            CompoundTag sections = chunk.getCompound("sections");
            for(String sectionKey : sections.getAllKeys()){
                int sectionIdx = Integer.parseInt(sectionKey);
                if(sectionIdx < 0 || sectionIdx > 512) throw new IllegalArgumentException("Invalid Toolgun plot section");
                CompoundTag data = sections.getCompound(sectionKey).getCompound("block_states");
                if(data.isEmpty()) continue;
                ListTag palette = data.getList("palette", Tag.TAG_COMPOUND);
                if(palette.isEmpty() || palette.size() > 4096) throw new IllegalArgumentException("Invalid Toolgun block palette");
                boolean[] skipped = new boolean[palette.size()];
                for(int idx = 0; idx < palette.size(); idx++){
                    var state = NbtUtils.readBlockState(registry, palette.getCompound(idx));
                    skipped[idx] = state.isAir() || state.is(Blocks.STRUCTURE_VOID);
                }
                int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
                int perLong = 64 / bits;
                long[] values = data.getLongArray("data");
                if(palette.size() > 1 && values.length != (4096 + perLong - 1) / perLong)
                    throw new IllegalArgumentException("Toolgun block data is truncated");
                long mask = (1L << bits) - 1;
                for(int idx = 0; idx < 4096; idx++){
                    int stateIdx = palette.size() == 1 ? 0 : (int) (values[idx / perLong] >>> ((idx % perLong) * bits) & mask);
                    if(stateIdx >= palette.size()) throw new IllegalArgumentException("Invalid Toolgun palette index");
                    CompoundTag state = palette.getCompound(stateIdx);
                    if(skipped[stateIdx]) continue;
                    BlockPos local = new BlockPos(pos.getMinBlockX() + (idx & 15),
                            minHeight + sectionIdx * 16 + (idx >> 8), pos.getMinBlockZ() + ((idx >> 4) & 15));
                    if(res.size() >= maximumBlocks) throw new IllegalArgumentException("Schematic exceeds the block limit");
                    res.add(new PlotBlock(local, state, entities.get(local)));
                }
            }
        }
        return res;
    }

    // Reject entity payloads instead of silently losing dynamic structures or duplicating inventories
    private static void requireBlockOnly(CompoundTag tag){
        for(String key : List.of("entities", "runtime_contraptions")){
            if(tag.contains(key) && (!(tag.get(key) instanceof ListTag list) || !list.isEmpty()))
                throw new IllegalArgumentException("This schematic contains entity structures; Digisable currently builds block structures");
        }
    }

    // Bound foreign body lists before allocating normalized tags
    private static void requireBodies(ListTag bodies, int maximum){
        if(bodies.isEmpty() || bodies.size() > maximum) throw new IllegalArgumentException("Schematic exceeds the body limit or has no bodies");
    }

    // Count all body blocks before copying their data
    private static int checkedCount(int count, int next, int maximum){
        if(next > maximum - count) throw new IllegalArgumentException("Schematic exceeds the block limit");
        return count + next;
    }

    // Find the first occupied plot corner
    private static BlockPos minimum(List<PlotBlock> blocks){
        return new BlockPos(blocks.stream().mapToInt(row -> row.pos().getX()).min().orElseThrow(),
                blocks.stream().mapToInt(row -> row.pos().getY()).min().orElseThrow(), blocks.stream().mapToInt(row -> row.pos().getZ()).min().orElseThrow());
    }

    // Find the last occupied plot corner
    private static BlockPos maximum(List<PlotBlock> blocks){
        return new BlockPos(blocks.stream().mapToInt(row -> row.pos().getX()).max().orElseThrow(),
                blocks.stream().mapToInt(row -> row.pos().getY()).max().orElseThrow(), blocks.stream().mapToInt(row -> row.pos().getZ()).max().orElseThrow());
    }

    // Recover the anchor used by older Toolgun saves
    private static Vector3d plotCenter(CompoundTag plot, double y){
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for(String key : plot.getCompound("chunks").getAllKeys()){
            ChunkPos pos = new ChunkPos(Long.parseLong(key));
            minX = Math.min(minX, pos.x); minZ = Math.min(minZ, pos.z);
            maxX = Math.max(maxX, pos.x); maxZ = Math.max(maxZ, pos.z);
        }
        return new Vector3d(((long) minX + maxX + 1) * 8D, y, ((long) minZ + maxZ + 1) * 8D);
    }

    // Validate numeric vector fields used by foreign file formats
    private static Vector3d vector(CompoundTag parent, String key){
        CompoundTag tag = parent.getCompound(key);
        for(String component : List.of("x", "y", "z"))
            if(!tag.contains(component, Tag.TAG_ANY_NUMERIC)) throw new IllegalArgumentException("Invalid schematic vector: " + key);
        Vector3d res = SableNBTUtils.readVector3d(tag);
        if(!Double.isFinite(res.x) || !Double.isFinite(res.y) || !Double.isFinite(res.z))
            throw new IllegalArgumentException("Invalid schematic vector: " + key);
        return res;
    }

    // Normalize valid orientations and retain older optional identity rotations
    private static Quaterniond quaternion(CompoundTag parent, String key, boolean optional){
        if(optional && !parent.contains(key)) return new Quaterniond();
        CompoundTag tag = parent.getCompound(key);
        for(String component : List.of("x", "y", "z", "w"))
            if(!tag.contains(component, Tag.TAG_ANY_NUMERIC)) throw new IllegalArgumentException("Invalid schematic orientation");
        Quaterniond res = SableNBTUtils.readQuaternion(tag);
        if(!Double.isFinite(res.lengthSquared()) || res.lengthSquared() < 1E-12)
            throw new IllegalArgumentException("Invalid schematic orientation");
        return res.normalize();
    }

    // Require complete integer block coordinates
    private static BlockPos integerPosition(CompoundTag tag){
        for(String component : List.of("x", "y", "z"))
            if(!tag.contains(component, Tag.TAG_INT)) throw new IllegalArgumentException("Invalid schematic block coordinates");
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    // Encode one normalized block palette reference
    private static CompoundTag row(BlockPos pos, int state){
        CompoundTag res = new CompoundTag();
        res.put("pos", coordinates(pos)); res.putInt("state", state);
        return res;
    }

    // Encode one independent body in the supported native structure format
    private static CompoundTag body(UUID id, Vector3d corner, Quaterniond turn, BlockPos size, ListTag palette, ListTag blocks){
        CompoundTag res = new CompoundTag();
        res.putUUID("uuid", id); res.put("position", SableNBTUtils.writeVector3d(corner));
        res.put("orientation", SableNBTUtils.writeQuaternion(turn)); res.put("size", coordinates(size));
        res.put("palette", palette); res.put("blocks", blocks);
        return res;
    }

    // Keep the root structure empty while preserving all imported bodies
    private static CompoundTag assembly(ListTag bodies){
        CompoundTag res = new CompoundTag(); res.put("sub_levels", bodies); return res;
    }

    // Retain source identities and coordinates for safe block entity reference remapping
    private static CompoundTag frame(String format, UUID originalId, BlockPos origin, int blueprintId){
        CompoundTag res = new CompoundTag(); res.putString("Format", format); res.putUUID("Original", originalId);
        res.put("Origin", coordinates(origin)); res.putInt("BlueprintId", blueprintId); return res;
    }

    // Write the native structure coordinate list
    private static ListTag coordinates(BlockPos pos){
        ListTag res = new ListTag(); res.add(IntTag.valueOf(pos.getX())); res.add(IntTag.valueOf(pos.getY())); res.add(IntTag.valueOf(pos.getZ())); return res;
    }

    // Retain native plot coordinates until body bounds have been calculated
    private record PlotBlock(BlockPos pos, CompoundTag state, CompoundTag data){}
}
