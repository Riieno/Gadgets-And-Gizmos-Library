package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicalConnectionApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermissionManager;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

// Copy connected world blocks into a portable template while their original plots remain live
public final class SubLevelSchematicCapture{
    // Keep capture on the owning server thread
    private SubLevelSchematicCapture(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read safe block configuration and connections without storing, extracting or removing the assembly
    public static SubLevelSchematic capture(ServerPlayer player, UUID rootId, double range,
                                             int maximumBodies, int maximumBlocks){
        ServerLevel level = player.serverLevel();
        if(!Double.isFinite(range) || range <= 0) throw new IllegalArgumentException("Invalid schematic capture range");
        return capture(level, rootId, maximumBodies, maximumBlocks, body -> validate(player, body, range));
    }

    // Let trusted server consumers supply access checks for every physically connected source body
    public static SubLevelSchematic capture(ServerLevel level, UUID rootId, int maximumBodies,
            int maximumBlocks, Consumer<ServerSubLevel> validate){
        if(!level.getServer().isSameThread() || SubLevelPhysicsSystem.IN_PHYSICS_STEP)
            throw new IllegalStateException("Schematic capture requires the server game thread");
        if(maximumBodies < 1 || maximumBlocks < 1 || validate == null)
            throw new IllegalArgumentException("Invalid schematic capture limits");
        var topology = SableAssemblyTopologyApi.discover(level, rootId);
        Map<UUID, ServerSubLevel> connected = new LinkedHashMap<>();
        topology.loadedBodies().forEach(body -> connected.put(body.getUniqueId(), body));
        for(UUID id : SablePhysicalConnectionApi.connectedBodyIds(level, connected.keySet())){
            if(SableLevelApi.subLevel(level, id) instanceof ServerSubLevel body) connected.putIfAbsent(id, body);
        }
        List<ServerSubLevel> bodies = List.copyOf(connected.values());
        if(!topology.available() || bodies.isEmpty()) throw new IllegalArgumentException("Sublevel is unavailable");
        if(bodies.size() > maximumBodies) throw new IllegalArgumentException("Assembly exceeds the schematic body limit");
        List<Captured> captures = new ArrayList<>();
        int count = 0;
        var prev = SubLevelSchematicSerializationContext.getCurrentContext();
        try{
            SubLevelSchematicSerializationContext.setCurrentContext(null);
            for(ServerSubLevel body : bodies){
                if(body.isRemoved() || SubLevelConstructionState.isBuilding(body))
                    throw new IllegalArgumentException("A schematic source body is unavailable or still building");
                if(!body.getPlot().getContraptions().isEmpty())
                    throw new IllegalArgumentException("Disassemble moving Create contraptions before saving a schematic");
                validate.accept(body);
                Captured captured = read(body, maximumBlocks - count);
                count += captured.blocks().size();
                captures.add(captured);
            }
            return template(level, rootId, captures);
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(prev); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply copy access to every connected body before reading its blocks
    private static void validate(ServerPlayer player, ServerSubLevel body, double range){
        ServerLevel level = player.serverLevel();
        Vec3 point = SubLevelArchiveApi.position(body);
        if(body.isRemoved() || body.getLevel() != level) throw new IllegalArgumentException("A connected sublevel is unavailable");
        if(SubLevelConstructionState.isBuilding(body)) throw new IllegalArgumentException("Wait for schematic construction to finish");
        if(point.distanceToSqr(player.position()) > range * range) throw new IllegalArgumentException("Assembly is outside schematic capture range");
        if(!ShipPermissionManager.get(player.server).allows(body.getUniqueId(), player.getUUID(), ShipPermission.STORE))
            throw new IllegalArgumentException("Ship schematic permission denied");
        if(!WorldAccessPolicy.canAccess(player, level, body.getUniqueId(), BlockPos.containing(point)))
            throw new IllegalArgumentException("Assembly contains protected sublevels");
        if(!body.getPlot().getContraptions().isEmpty()) throw new IllegalArgumentException("Disassemble moving Create contraptions before saving a schematic");
        if(body.getPlot().getLoadedChunks().size() > 256) throw new IllegalArgumentException("Sublevel exceeds the loaded chunk limit");
    }

    // Read actual chunk and block entities without recreating them at old plot coordinates
    private static Captured read(ServerSubLevel body, int remaining){
        List<SavedBlock> blocks = new ArrayList<>();
        for(var holder : body.getPlot().getLoadedChunks()){
            var chunk = holder.getChunk();
            if(chunk == null) throw new IllegalArgumentException("A schematic source chunk is unavailable");
            chunk.findBlocks(state -> !state.isAir(), (pos, state) -> {
                if(blocks.size() >= remaining) throw new IllegalArgumentException("Assembly exceeds the schematic block limit");
                BlockEntity be = state.hasBlockEntity() ? SubLevelBlockEntityCollector.getOrCreateBlockEntity(body, pos) : null;
                // Plain Create tracks declare entity support but intentionally have no entity in this state
                CompoundTag data = be == null ? new CompoundTag() : be.saveWithFullMetadata(body.getLevel().registryAccess());
                blocks.add(new SavedBlock(pos.immutable(), state, be, data));
            });
        }
        if(blocks.isEmpty()) throw new IllegalArgumentException("A schematic source body has no blocks");
        BoundingBox3i bounds = BoundingBox3i.from(blocks.stream().map(SavedBlock::pos).toList());
        BlockPos min = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        BlockPos size = new BlockPos(bounds.maxX() - bounds.minX() + 1, bounds.maxY() - bounds.minY() + 1,
                bounds.maxZ() - bounds.minZ() + 1);
        int maximum = SubLevelSchematicFiles.MAXIMUM_BODY_SIDE;
        if(size.getX() > maximum || size.getY() > maximum || size.getZ() > maximum)
            throw new IllegalArgumentException("Schematic dimensions exceed the limit");
        Pose3d pose = new Pose3d().set(body.logicalPose());
        Vector3d point = pose.transformPosition(new Vector3d(min.getX(), min.getY(), min.getZ()));
        return new Captured(body, pose, min, size, new Vec3(point.x, point.y, point.z), blocks);
    }

    // Give every copied body fresh template coordinates while retaining its relative world pose
    private static SubLevelSchematic template(ServerLevel level, UUID rootId, List<Captured> captures){
        Vec3 anchor = captures.stream().filter(val -> val.body().getUniqueId().equals(rootId))
                .findFirst().orElseThrow().corner();
        Map<UUID, Captured> originals = new LinkedHashMap<>();
        Map<UUID, SubLevelSchematicSerializationContext.SchematicMapping> mappings = new LinkedHashMap<>();
        for(Captured val : captures){
            UUID id = val.body().getUniqueId();
            originals.put(id, val);
            Vec3 corner = val.corner().subtract(anchor);
            mappings.put(id, new SubLevelSchematicSerializationContext.SchematicMapping(
                    new Vector3d(corner.x, corner.y, corner.z), val.pose().orientation(), UUID.randomUUID(),
                    pos -> ((BlockPos)pos).subtract(val.min())));
        }
        List<SchematicConnectionData.BodyFrame> frames = captures.stream().map(val ->
                new SchematicConnectionData.BodyFrame(val.body().getUniqueId(), mappings.get(val.body().getUniqueId()).newUUID(),
                        val.min(), val.size(), anchor)).toList();
        List<SubLevelSchematic.Body> bodies = new ArrayList<>();
        var prev = SubLevelSchematicSerializationContext.getCurrentContext();
        try{
            for(Captured val : captures){
                var ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.SAVE,
                        BoundingBox3i.from(val.blocks().stream().map(SavedBlock::pos).toList()));
                ctx.getMappings().putAll(mappings);
                ctx.setPlaceTransform(pos -> ((BlockPos)pos).subtract(val.min()));
                ctx.setSetupTransform(pos -> ((BlockPos)pos).subtract(val.min()));
                SubLevelSchematicSerializationContext.setCurrentContext(ctx);
                List<SubLevelSchematic.Block> blocks = new ArrayList<>();
                for(SavedBlock block : val.blocks()){
                    CompoundTag safe = SchematicBlockData.safe(level, block.state(), block.be());
                    SchematicConnectionData.capture(level, block.be(), block.data(), safe, val.body().getUniqueId(), frames);
                    blocks.add(new SubLevelSchematic.Block(block.pos().subtract(val.min()), block.state(), safe));
                }
                bodies.add(new SubLevelSchematic.Body(mappings.get(val.body().getUniqueId()).newUUID(),
                        val.corner().subtract(anchor), val.pose().orientation(), val.size(), blocks));
            }
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(prev); }
        List<SubLevelSchematic.Joint> joints = new ArrayList<>();
        for(Captured val : captures) for(var joint : SubLevelSchematicJoints.retained(val.body())){
            Captured first = originals.get(joint.first()), second = originals.get(joint.second());
            if(first == null || second == null) throw new IllegalArgumentException("Schematic weld is missing a connected body");
            joints.add(new SubLevelSchematic.Joint(mappings.get(joint.first()).newUUID(), mappings.get(joint.second()).newUUID(),
                    joint.type(), joint.firstAnchor().subtract(Vec3.atLowerCornerOf(first.min())),
                    joint.secondAnchor().subtract(Vec3.atLowerCornerOf(second.min())), joint.orientation(), joint.firstAxis(), joint.secondAxis()));
        }
        return new SubLevelSchematic(bodies, joints);
    }

    // Retain source data until the safe write and connection capture complete
    private record SavedBlock(BlockPos pos, BlockState state, BlockEntity be, CompoundTag data){}

    // Keep one stable source pose and its copied bounds during the synchronous capture
    private record Captured(ServerSubLevel body, Pose3d pose, BlockPos min, BlockPos size, Vec3 corner, List<SavedBlock> blocks){}
}
