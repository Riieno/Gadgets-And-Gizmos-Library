package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.Codec;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Convert complete archived native plots into independent schematic templates
public final class SubLevelSchematicArchive{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Codec<PalettedContainer<BlockState>> STATES = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES,
            Blocks.AIR.defaultBlockState());

    private SubLevelSchematicArchive(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Export safe configuration without extracting or consuming the archive
    public static SubLevelSchematic capture(ServerPlayer player, UUID archiveId, int maximumBodies, int maximumBlocks) throws IOException{
        if(!player.server.isSameThread()) throw new IllegalStateException("Schematic export requires the server thread");
        CompoundTag archive = SubLevelArchiveStore.forServer(player.server).entry(archiveId);
        if(!"stored".equals(archive.getString("State"))) throw new IllegalArgumentException("Stored sublevel is unavailable");
        if(!player.hasPermissions(2) && !player.getUUID().equals(archive.getUUID("Owner"))) throw new IllegalArgumentException("This archive belongs to another player");
        ServerLevel level = player.serverLevel();
        if(!level.dimension().location().toString().equals(archive.getString("Dimension"))) throw new IllegalArgumentException("Export this assembly in its original dimension");
        ListTag saved = archive.getList("Bodies", Tag.TAG_COMPOUND);
        if(saved.isEmpty() || saved.size() > maximumBodies) throw new IllegalArgumentException("Archive exceeds the body limit");
        List<Captured> captures = new ArrayList<>();
        int count = 0;
        for(int idx = 0; idx < saved.size(); idx++){
            Captured body = decodePlot(level, saved.getCompound(idx), maximumBlocks);
            count += body.blocks().size();
            if(count > maximumBlocks) throw new IllegalArgumentException("Archive exceeds the block limit");
            captures.add(body);
        }
        Vec3 anchor = captures.stream().filter(body -> body.id().equals(archive.getUUID("Root")))
                .findFirst().orElse(captures.getFirst()).corner();
        var ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.SAVE, null);
        for(Captured body : captures){
            ctx.getMappings().put(body.id(), new SubLevelSchematicSerializationContext.SchematicMapping(
                    new org.joml.Vector3d(body.corner().x - anchor.x, body.corner().y - anchor.y, body.corner().z - anchor.z),
                    body.pose().orientation(), UUID.randomUUID(), pos -> ((BlockPos)pos).subtract(body.min())));
        }
        var prev = SubLevelSchematicSerializationContext.getCurrentContext();
        List<SubLevelSchematic.Body> bodies = new ArrayList<>();
        try{
            // Load original data before enabling SAVE so archive references are not remapped while reading
            Map<SubLevelSchematic.Block, BlockEntity> entities = new HashMap<>();
            for(Captured body : captures) for(var block : body.blocks()){
                if(block.data().isEmpty()) continue;
                BlockEntity be = SchematicBlockData.create(level, block.pos(), block.state(), block.data());
                if(be == null) throw new IllegalArgumentException("Archived block entity is unavailable");
                be.setLevel(level);
                entities.put(block, be);
            }
            SubLevelSchematicSerializationContext.setCurrentContext(ctx);
            for(Captured body : captures){
                List<SubLevelSchematic.Block> blocks = new ArrayList<>();
                for(var block : body.blocks()){
                    CompoundTag data = SchematicBlockData.safe(level, block.state(), entities.get(block));
                    blocks.add(new SubLevelSchematic.Block(block.pos().subtract(body.min()), block.state(), data));
                }
                bodies.add(new SubLevelSchematic.Body(ctx.getMapping(body.id()).newUUID(), body.corner().subtract(anchor),
                        new Quaterniond(body.pose().orientation()), body.size(), blocks));
            }
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(prev); }
        Map<UUID, Captured> originals = new HashMap<>();
        captures.forEach(body -> originals.put(body.id(), body));
        List<SubLevelSchematic.Joint> joints = new ArrayList<>();
        for(int idx = 0; idx < saved.size(); idx++){
            CompoundTag src = saved.getCompound(idx);
            for(var joint : SubLevelSchematicJoints.retained(src.getCompound("user_data"), src.getUUID("uuid"))){
                var first = originals.get(joint.first()); var second = originals.get(joint.second());
                if(first == null || second == null) throw new IllegalArgumentException("Archived schematic weld is missing a body");
                joints.add(new SubLevelSchematic.Joint(ctx.getMapping(first.id()).newUUID(), ctx.getMapping(second.id()).newUUID(), joint.type(),
                        joint.firstAnchor().subtract(Vec3.atLowerCornerOf(first.min())), joint.secondAnchor().subtract(Vec3.atLowerCornerOf(second.min())),
                        joint.orientation(), joint.firstAxis(), joint.secondAxis()));
            }
        }
        return new SubLevelSchematic(bodies, joints);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Decode every saved section rather than the archive's sampled preview
    private static Captured decodePlot(ServerLevel level, CompoundTag saved, int maximumBlocks){
        CompoundTag plot = saved.getCompound("plot");
        int logSize = plot.getInt("log_size");
        if(logSize < 1 || logSize > 10) throw new IllegalArgumentException("Invalid archived plot size");
        var origin = SubLevelContainer.getContainer(level).getOrigin();
        int baseX = (plot.getInt("plot_x") + origin.x) << (logSize + 4);
        int baseZ = (plot.getInt("plot_z") + origin.y) << (logSize + 4);
        CompoundTag chunks = plot.getCompound("chunks");
        Map<BlockPos, CompoundTag> entities = new HashMap<>();
        for(String key : chunks.getAllKeys()){
            ListTag rows = chunks.getCompound(key).getList("block_entities", Tag.TAG_COMPOUND);
            for(int idx = 0; idx < rows.size(); idx++){
                CompoundTag row = rows.getCompound(idx);
                entities.put(new BlockPos(row.getInt("x"), row.getInt("y"), row.getInt("z")), row);
            }
        }
        List<SubLevelSchematic.Block> blocks = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for(String key : chunks.getAllKeys()){
            ChunkPos chunk = new ChunkPos(Long.parseLong(key));
            CompoundTag sections = chunks.getCompound(key).getCompound("sections");
            for(String sectionKey : sections.getAllKeys()){
                int sectionIdx = Integer.parseInt(sectionKey);
                if(sectionIdx < 0 || sectionIdx >= level.getSectionsCount()) throw new IllegalArgumentException("Invalid archived section");
                int baseY = level.getSectionYFromSectionIndex(sectionIdx) << 4;
                var states = STATES.parse(NbtOps.INSTANCE, sections.getCompound(sectionKey).getCompound("block_states")).getOrThrow();
                for(int y = 0; y < 16; y++) for(int z = 0; z < 16; z++) for(int x = 0; x < 16; x++){
                    BlockState state = states.get(x, y, z);
                    if(state.isAir()) continue;
                    BlockPos pos = new BlockPos(baseX + chunk.getMinBlockX() + x, baseY + y, baseZ + chunk.getMinBlockZ() + z);
                    blocks.add(new SubLevelSchematic.Block(pos, state, entities.get(pos)));
                    if(blocks.size() > maximumBlocks) throw new IllegalArgumentException("Archive exceeds the block limit");
                    minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
                    maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
                }
            }
        }
        if(blocks.isEmpty()) throw new IllegalArgumentException("Archived body contains no blocks");
        BlockPos min = new BlockPos(minX, minY, minZ);
        Pose3d pose = SubLevelSerializer.fromData(saved).pose();
        var corner = pose.transformPosition(new org.joml.Vector3d(minX, minY, minZ));
        return new Captured(saved.getUUID("uuid"), pose, min, new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1),
                new Vec3(corner.x, corner.y, corner.z), blocks);
    }

    // Keep the original plot coordinates until SAVE callbacks finish remapping them
    private record Captured(UUID id, Pose3d pose, BlockPos min, BlockPos size, Vec3 corner, List<SubLevelSchematic.Block> blocks){}
}
