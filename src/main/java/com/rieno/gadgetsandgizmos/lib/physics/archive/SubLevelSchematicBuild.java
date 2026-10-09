package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.simibubi.create.api.schematic.state.SchematicStateFilter;
import com.simibubi.create.api.schematic.state.SchematicStateFilterRegistry;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Construct fresh sublevels over ticks while keeping their placement and reference mapping stable
public final class SubLevelSchematicBuild implements AutoCloseable{
    // Own the player, world and immutable placement plan for this construction
    private final ServerPlayer player;
    private final ServerLevel level;
    private final SubLevelSchematic schematic;
    private final Vec3 target;
    private final Quaterniond turn;
    private final List<Placement> placements;
    private final List<List<Placement>> sections;
    private final int[] sectionPlaced;
    private final List<ServerSubLevel> bodies = new ArrayList<>();
    private final SableSubLevelResidency.Lease lease = SableSubLevelResidency.lease("schematic-build:" + UUID.randomUUID());
    private final SubLevelSchematicSerializationContext ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null);
    // Track the bounded server tick progression and completion state
    private int placed;
    private boolean complete;
    private boolean closed;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Validate the full destination before allocating fresh body identities
    public SubLevelSchematicBuild(ServerPlayer player, SubLevelSchematic schematic, Vec3 target, Quaterniondc rotation){
        this(player, schematic, target, rotation, 1);
    }

    // Divide construction into independent spatial sections while retaining one placement transaction
    public SubLevelSchematicBuild(ServerPlayer player, SubLevelSchematic schematic, Vec3 target, Quaterniondc rotation, int maximumSections){
        this.player = player; this.level = player.serverLevel(); this.schematic = schematic; this.target = target;
        checkThread();
        if(schematic.bodies().isEmpty() || target == null || !Double.isFinite(target.x + target.y + target.z)
                || rotation == null || !Double.isFinite(rotation.lengthSquared())
                || Math.abs(rotation.lengthSquared() - 1) > 0.01) throw new IllegalArgumentException("Invalid schematic placement");
        turn = new Quaterniond(rotation).normalize();
        placements = placements(schematic, target, turn);
        sections = sections(placements, maximumSections);
        sectionPlaced = new int[sections.size()];
        for(var body : schematic.bodies()){
            AABB local = new AABB(0, 0, 0, body.size().getX(), body.size().getY(), body.size().getZ());
            Quaterniond orientation = new Quaterniond(turn).mul(body.orientation());
            SubLevelArchivePlacement.validate(player, level, SubLevelArchiveTransform.bounds(local, Vec3.ZERO, worldCorner(body), orientation));
        }
        try{
            var container = SubLevelContainer.getContainer(level);
            for(var body : schematic.bodies()){
                var next = (ServerSubLevel) container.allocateNewSubLevel(new Pose3d());
                bodies.add(next);
                BlockPos center = next.getPlot().getCenterBlock();
                if(body.size().getX() >= (1 << (container.getLogPlotSize() + 3))
                        || body.size().getZ() >= (1 << (container.getLogPlotSize() + 3))
                        || center.getY() + body.size().getY() > level.getMaxBuildHeight()) throw new IllegalArgumentException("Schematic does not fit in a native sublevel plot");
                Vec3 corner = worldCorner(body);
                Pose3d pose = new Pose3d();
                pose.position().set(corner.x, corner.y, corner.z);
                pose.rotationPoint().set(center.getX(), center.getY(), center.getZ());
                pose.orientation().set(new Quaterniond(turn).mul(body.orientation()));
                next.logicalPose().set(pose);
                next.updateLastPose();
                SubLevelConstructionState.retain(next, pose);
                container.physicsSystem().getPipeline().onStatsChanged(next);
                lease.retain(next);
                ctx.getMappings().put(body.id(), new SubLevelSchematicSerializationContext.SchematicMapping(
                        null, null, next.getUniqueId(), pos -> ((BlockPos)pos).offset(center)));
            }
            ctx.setSetupTransform(pos -> (BlockPos)pos);
            ctx.setPlaceTransform(pos -> BlockPos.containing(transform(((BlockPos)pos).getCenter(), target, turn)));
            holdPoses();
        }catch(RuntimeException err){ close(); throw err; }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Place a bounded batch in deterministic bottom-to-top order
    public List<Vec3> tick(int maximumBlocks){
        return tickSections(maximumBlocks).stream().flatMap(row -> row.points().stream()).toList();
    }

    // Advance each section independently and report its placed blocks and next visual target
    public List<SectionProgress> tickSections(int maximumBlocks){
        checkThread();
        if(closed || complete) return List.of();
        if(maximumBlocks < 0) throw new IllegalArgumentException("Build batches cannot be negative");
        if(player.server.getPlayerList().getPlayer(player.getUUID()) != player || player.serverLevel() != level)
            throw new IllegalArgumentException("Schematic construction stopped because its player left the world");
        var container = SubLevelContainer.getContainer(level);
        for(ServerSubLevel body : bodies) if(body.isRemoved() || container.getSubLevel(body.getUniqueId()) != body)
            throw new IllegalArgumentException("A schematic body became unavailable");
        List<List<Vec3>> points = new ArrayList<>();
        for(int idx = 0; idx < sections.size(); idx++) points.add(new ArrayList<>());
        var prev = SubLevelSchematicSerializationContext.getCurrentContext();
        try{
            SubLevelSchematicSerializationContext.setCurrentContext(ctx);
            int end = Math.min(placements.size(), placed + maximumBlocks);
            while(placed < end){
                int section = nextSection();
                Placement placement = sections.get(section).get(sectionPlaced[section]);
                if(!level.hasChunkAt(BlockPos.containing(placement.worldPos()))
                        || !WorldAccessPolicy.canAccess(player, level, null, BlockPos.containing(placement.worldPos())))
                    throw new IllegalArgumentException("Schematic destination became unloaded or protected");
                ServerSubLevel body = bodies.get(placement.bodyIdx());
                BlockPos pos = body.getPlot().getCenterBlock().offset(placement.block().pos());
                ChunkPos chunk = new ChunkPos(pos);
                if(body.getPlot().getChunkHolder(body.getPlot().toLocal(chunk)) == null) body.getPlot().newEmptyChunk(chunk);
                var state = placement.block().state();
                var filter = SchematicStateFilterRegistry.REGISTRY.get(state);
                if(filter != null) state = filter.filterStates(null, state);
                else if(state.getBlock() instanceof SchematicStateFilter special) state = special.filterStates(null, state);
                CompoundTag data = placement.block().data();
                if(!data.isEmpty()){
                    data.putInt("x", pos.getX()); data.putInt("y", pos.getY()); data.putInt("z", pos.getZ());
                }
                BlockHelper.placeSchematicBlock(level, state, pos, ItemStack.EMPTY, data.isEmpty() ? null : data);
                if(level.getBlockState(pos).isAir() && !state.isAir()) throw new IllegalArgumentException("A schematic block could not be placed");
                points.get(section).add(placement.worldPos());
                sectionPlaced[section]++;
                placed++;
            }
            holdPoses();
            if(placed == placements.size()){
                for(ServerSubLevel body : bodies) if(body.getSelfMassTracker().isInvalid())
                    throw new IllegalArgumentException("A completed schematic body has no physical mass");
                SubLevelSchematicJoints.install(level, schematic, bodies);
                complete = true;
                bodies.forEach(SubLevelConstructionState::release);
                lease.close();
            }
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(prev); }
        List<SectionProgress> res = new ArrayList<>();
        for(int idx = 0; idx < sections.size(); idx++) res.add(sectionProgress(idx, points.get(idx)));
        return List.copyOf(res);
    }

    // Expose the current visual and material progress
    public int placedBlocks(){ return placed; }
    // Expose the total number of blocks in this construction
    public int totalBlocks(){ return placements.size(); }
    // Report completion without consuming the reusable schematic
    public boolean complete(){ return complete; }
    // Return the fresh assembly identities
    public List<UUID> bodyIds(){ return bodies.stream().map(ServerSubLevel::getUniqueId).toList(); }

    // Expose section targets before the first construction batch is placed
    public List<SectionProgress> sectionProgress(){
        List<SectionProgress> res = new ArrayList<>();
        for(int idx = 0; idx < sections.size(); idx++) res.add(sectionProgress(idx, List.of()));
        return List.copyOf(res);
    }

    // Remove an incomplete assembly before the caller refunds its reserved materials
    @Override public void close(){
        checkThread();
        if(closed) return;
        closed = true;
        lease.close();
        bodies.forEach(SubLevelConstructionState::release);
        if(!complete && !bodies.isEmpty()){
            var container = SubLevelContainer.getContainer(level);
            List<ServerSubLevel> loaded = bodies.stream().filter(body -> container.getSubLevel(body.getUniqueId()) == body).toList();
            if(!loaded.isEmpty()) SableSubLevelLifecycleApi.remove(level, loaded);
        }
    }

    // Order visual layers across every rotated body with stable ordering within each layer
    public static List<Placement> placements(SubLevelSchematic schematic, Vec3 target, Quaterniondc rotation){
        List<Placement> res = new ArrayList<>();
        for(int idx = 0; idx < schematic.bodies().size(); idx++){
            var body = schematic.bodies().get(idx);
            Quaterniond orientation = new Quaterniond(rotation).mul(body.orientation());
            Vec3 corner = transform(body.corner(), target, rotation);
            for(var block : body.blocks()) res.add(new Placement(idx, block, transform(block.pos().getCenter(), corner, orientation)));
        }
        res.sort(Comparator.comparingInt((Placement row) -> (int) Math.floor(row.worldPos().y))
                .thenComparingInt(Placement::bodyIdx).thenComparingInt(row -> row.block().pos().getY())
                .thenComparingInt(row -> row.block().pos().getZ()).thenComparingInt(row -> row.block().pos().getX()));
        return List.copyOf(res);
    }

    // Keep columns together in balanced sections and build each section from its lowest layer
    public static List<List<Placement>> sections(List<Placement> placements, int maximumSections){
        if(maximumSections < 1 || maximumSections > 64) throw new IllegalArgumentException("Construction requires 1-64 sections");
        if(placements.isEmpty()) return List.of();
        List<List<Placement>> res = new ArrayList<>();
        res.add(new ArrayList<>(placements));
        while(res.size() < maximumSections){
            int selected = -1;
            for(int idx = 0; idx < res.size(); idx++)
                if(res.get(idx).size() > 1 && (selected < 0 || res.get(idx).size() > res.get(selected).size())) selected = idx;
            if(selected < 0) break;
            List<Placement> group = res.get(selected);
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            for(Placement row : group){
                minX = Math.min(minX, row.worldPos().x); maxX = Math.max(maxX, row.worldPos().x);
                minZ = Math.min(minZ, row.worldPos().z); maxZ = Math.max(maxZ, row.worldPos().z);
            }
            int axis = Math.max(maxX - minX, maxZ - minZ) < 1.0E-6 ? 1 : maxX - minX >= maxZ - minZ ? 0 : 2;
            group.sort(Comparator.comparingDouble(row -> coordinate(row.worldPos(), axis)));
            int split = -1;
            for(int idx = 1; idx < group.size(); idx++){
                if(Math.abs(coordinate(group.get(idx).worldPos(), axis) - coordinate(group.get(idx - 1).worldPos(), axis)) < 1.0E-6) continue;
                if(split < 0 || Math.abs(idx * 2 - group.size()) < Math.abs(split * 2 - group.size())) split = idx;
            }
            if(split < 0) break;
            res.set(selected, new ArrayList<>(group.subList(0, split)));
            res.add(selected + 1, new ArrayList<>(group.subList(split, group.size())));
        }
        Comparator<Placement> layers = Comparator.comparingInt((Placement row) -> (int) Math.floor(row.worldPos().y))
                .thenComparingInt(Placement::bodyIdx).thenComparingInt(row -> row.block().pos().getY())
                .thenComparingInt(row -> row.block().pos().getZ()).thenComparingInt(row -> row.block().pos().getX());
        res.forEach(group -> group.sort(layers));
        return res.stream().map(List::copyOf).toList();
    }

    // Share each batch between sections according to their remaining progress
    private int nextSection(){
        int selected = -1;
        for(int idx = 0; idx < sections.size(); idx++){
            if(sectionPlaced[idx] == sections.get(idx).size()) continue;
            if(selected < 0 || (long) sectionPlaced[idx] * sections.get(selected).size()
                    < (long) sectionPlaced[selected] * sections.get(idx).size()) selected = idx;
        }
        return selected;
    }

    // Keep completed sections without a pending block target
    private SectionProgress sectionProgress(int idx, List<Vec3> points){
        var blocks = sections.get(idx);
        Vec3 next = sectionPlaced[idx] == blocks.size() ? null : blocks.get(sectionPlaced[idx]).worldPos();
        return new SectionProgress(idx, sectionPlaced[idx], blocks.size(), points, next);
    }

    // Compare one world axis when splitting spatial sections
    private static double coordinate(Vec3 pos, int axis){ return axis == 0 ? pos.x : axis == 1 ? pos.y : pos.z; }

    // Keep partially built bodies at their intended pose until their last block is present
    private void holdPoses(){
        var pipeline = SubLevelContainer.getContainer(level).physicsSystem().getPipeline();
        for(int idx = 0; idx < bodies.size(); idx++){
            ServerSubLevel body = bodies.get(idx);
            body.updateMergedMassData(1);
            Pose3d pose = SubLevelConstructionState.pose(body);
            body.logicalPose().set(pose);
            pipeline.teleport(body, pose.position(), pose.orientation());
            pipeline.resetVelocity(body);
            var bounds = body.getPlot().getBoundingBox();
            if(bounds.minX() <= bounds.maxX() && bounds.minY() <= bounds.maxY() && bounds.minZ() <= bounds.maxZ()){
                body.updateBoundingBox(); body.forceUpdateGlobalBounds();
            }
            body.updateLastPose();
        }
    }

    // Transform one body's schematic corner into the chosen world placement
    private Vec3 worldCorner(SubLevelSchematic.Body body){ return transform(body.corner(), target, turn); }
    // Rotate a local point around the schematic origin
    private static Vec3 transform(Vec3 pos, Vec3 origin, Quaterniondc rotation){
        Vector3d val = rotation.transform(new Vector3d(pos.x, pos.y, pos.z));
        return origin.add(val.x, val.y, val.z);
    }
    // Reject mutation outside the owning server thread
    private void checkThread(){
        if(!level.getServer().isSameThread()) throw new IllegalStateException("Schematic construction requires the server thread");
    }

    // Expose one scheduled block and its world-space visual target
    public record Placement(int bodyIdx, SubLevelSchematic.Block block, Vec3 worldPos){}

    // Associate construction progress and visual targets with one stable section
    public record SectionProgress(int sectionIdx, int placedBlocks, int totalBlocks, List<Vec3> points, Vec3 nextBlock){
        public SectionProgress{ points = List.copyOf(points); }
    }
}
