package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermissionManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Move complete native plots into durable archives and extract each archive at most once
public final class SubLevelArchiveApi{
    private SubLevelArchiveApi(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Capture every connected body before removing any of them
    public static UUID store(ServerPlayer player, ServerLevel level, UUID rootId, Limits limits) throws IOException{
        checkThread(level);
        var topology = SableAssemblyTopologyApi.discover(level, rootId);
        List<ServerSubLevel> bodies = topology.loadedBodies();
        if(!topology.available() || bodies.isEmpty()) throw new IllegalArgumentException("Sublevel is unavailable");
        if(bodies.size() > limits.maximumBodies()) throw new IllegalArgumentException("Assembly exceeds the body limit");
        var store = SubLevelArchiveStore.forServer(level.getServer());
        long stored = store.catalogue(player.getUUID(), false).size();
        if(stored >= limits.maximumArchives()) throw new IllegalArgumentException("Your sublevel storage limit is reached");
        for(ServerSubLevel body : bodies){
            Vec3 point = position(body);
            if(point.distanceToSqr(player.position()) > limits.storeRange() * limits.storeRange()) throw new IllegalArgumentException("Assembly is outside storage range");
            if(!ShipPermissionManager.get(player.server).allows(body.getUniqueId(), player.getUUID(), ShipPermission.STORE)) throw new IllegalArgumentException("Ship storage permission denied");
            if(!WorldAccessPolicy.canAccess(player, level, body.getUniqueId(), BlockPos.containing(point))) throw new IllegalArgumentException("Assembly contains protected sublevels");
            if(!body.getPlot().getContraptions().isEmpty()) throw new IllegalArgumentException("Disassemble moving Create contraptions before storing this assembly");
            if(body.getPlot().getLoadedChunks().size() > 256) throw new IllegalArgumentException("Sublevel exceeds the loaded chunk limit");
        }
        int blocks = SubLevelSnapshots.blockCount(bodies, limits.maximumBlocks());
        if(blocks > limits.maximumBlocks()) throw new IllegalArgumentException("Assembly exceeds the block limit");
        CompoundTag tag = new CompoundTag();
        UUID id = UUID.randomUUID();
        tag.putUUID("Id", id);
        tag.putUUID("Owner", player.getUUID());
        tag.putUUID("Root", rootId);
        tag.putString("Dimension", level.dimension().location().toString());
        String name = bodies.stream().filter(body -> body.getUniqueId().equals(rootId)).findFirst().orElse(bodies.getFirst()).getName();
        tag.putString("Name", name == null ? "" : name);
        tag.putString("State", "storing");
        tag.putInt("Blocks", blocks);
        tag.putInt("BodyCount", bodies.size());
        tag.putLong("Created", System.currentTimeMillis());
        ListTag serialized = new ListTag();
        List<UUID> ids = bodies.stream().map(ServerSubLevel::getUniqueId).toList();
        for(ServerSubLevel body : bodies){
            serialized.add(SubLevelSerializer.toData(body, ids.stream().filter(val -> !val.equals(body.getUniqueId())).toList()).fullTag());
        }
        tag.put("Bodies", serialized);
        tag.put("Preview", SubLevelSnapshots.preview(bodies, 2048));
        store.write(tag);
        var container = SubLevelContainer.getContainer(level);
        for(ServerSubLevel body : bodies){
            body.getPlot().kickAllEntities();
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        container.getHoldingChunkMap().saveAll();
        player.server.saveEverything(false, true, true);
        tag.putString("State", "stored");
        store.write(tag);
        SubLevelArchiveStore.reservePlots(container);
        return id;
    }

    // Restore original identities and plot addresses, consuming the archive after a durable world save
    public static UUID extract(ServerPlayer player, UUID archiveId, Vec3 target) throws IOException{
        return extract(player, archiveId, target, new Quaterniond(), false);
    }

    // Restore an archive around its visual preview anchor at a chosen orientation
    public static UUID extractPlaced(ServerPlayer player, UUID archiveId, Vec3 target,
                                     Quaterniondc rotation) throws IOException{
        return extract(player, archiveId, target, rotation, true);
    }

    private static UUID extract(ServerPlayer player, UUID archiveId, Vec3 target,
                                Quaterniondc rotation, boolean previewAnchor) throws IOException{
        if(target == null || !Double.isFinite(target.x + target.y + target.z)) throw new IllegalArgumentException("Invalid extraction position");
        if(rotation == null || !Double.isFinite(rotation.x() + rotation.y() + rotation.z() + rotation.w())
                || Math.abs(rotation.lengthSquared() - 1.0D) > 0.01D) throw new IllegalArgumentException("Invalid extraction rotation");
        Quaterniond turn = new Quaterniond(rotation).normalize();
        var store = SubLevelArchiveStore.forServer(player.server);
        CompoundTag tag = store.entry(archiveId);
        if(tag.isEmpty() || "consumed".equals(tag.getString("State"))) throw new IllegalArgumentException("Stored sublevel is unavailable");
        if(previewAnchor && !"stored".equals(tag.getString("State"))) throw new IllegalArgumentException("Stored sublevel is unavailable");
        if(!player.hasPermissions(2) && !player.getUUID().equals(tag.getUUID("Owner"))) throw new IllegalArgumentException("This archive belongs to another player");
        ServerLevel level = player.serverLevel();
        checkThread(level);
        if(!level.dimension().location().toString().equals(tag.getString("Dimension"))) throw new IllegalArgumentException("Extract this assembly in its original dimension");
        if(!level.getWorldBorder().isWithinBounds(BlockPos.containing(target)) || !level.hasChunkAt(BlockPos.containing(target))) throw new IllegalArgumentException("Extraction location is unavailable");
        if(!WorldAccessPolicy.canAccess(player, level, null, BlockPos.containing(target))) throw new IllegalArgumentException("Extraction location is protected");
        var container = SubLevelContainer.getContainer(level);
        ListTag bodies = tag.getList("Bodies", Tag.TAG_COMPOUND);
        if(bodies.isEmpty()) throw new IOException("Archive contains no sublevels");
        Vec3 anchor = previewAnchor ? previewAnchor(tag, bodies)
                : position(SubLevelSerializer.fromData(bodies.getCompound(0)).pose());
        for(int idx = 0; idx < bodies.size(); idx++){
            CompoundTag body = bodies.getCompound(idx);
            CompoundTag plot = body.getCompound("plot");
            if(container.getSubLevel(body.getUUID("uuid")) != null || container.getSubLevel(plot.getInt("plot_x"), plot.getInt("plot_z")) != null) throw new IllegalArgumentException("An archive transaction still has live bodies; extraction was blocked");
            if(container.getHoldingChunkMap().getHoldingSubLevel(body.getUUID("uuid")) != null) throw new IllegalArgumentException("An original sublevel is still in native storage; extraction was blocked");
            var bounds = SubLevelSerializer.fromData(body).bounds();
            AABB oldBox = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ());
            SubLevelArchivePlacement.validate(player, level,
                    SubLevelArchiveTransform.bounds(oldBox, anchor, target, turn));
        }
        tag.putString("State", "extracting");
        store.write(tag);
        List<ServerSubLevel> restored = new ArrayList<>();
        boolean committed = false;
        try{
            for(int idx = 0; idx < bodies.size(); idx++){
                CompoundTag body = bodies.getCompound(idx).copy();
                var source = SubLevelSerializer.fromData(body);
                var pose = source.pose();
                Vec3 next = SubLevelArchiveTransform.point(position(pose), anchor, target, turn);
                pose.position().set(next.x, next.y, next.z);
                pose.orientation().premul(turn);
                body.put("pose", SableNBTUtils.writePose3d(pose));
                var bounds = source.bounds();
                AABB box = SubLevelArchiveTransform.bounds(new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                        bounds.maxX(), bounds.maxY(), bounds.maxZ()), anchor, target, turn);
                body.put("world_bounds", SableNBTUtils.writeBoundingBox(new BoundingBox3d(box)));
                CompoundTag plot = body.getCompound("plot");
                container.getOccupancy().clear(container.getIndex(plot.getInt("plot_x"), plot.getInt("plot_z")));
                ServerSubLevel loaded = SubLevelSerializer.fullyLoad(level, SubLevelSerializer.fromData(body));
                if(loaded == null) throw new IOException("Sable rejected a stored sublevel");
                restored.add(loaded);
            }
            container.getHoldingChunkMap().saveAll();
            player.server.saveEverything(false, true, true);
            committed = true;
            store.consume(tag);
            return tag.getUUID("Root");
        }catch(IOException | RuntimeException err){
            if(committed) throw err;
            for(int idx = 0; idx < bodies.size(); idx++){
                var body = container.getSubLevel(bodies.getCompound(idx).getUUID("uuid"));
                if(body != null) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            }
            container.getHoldingChunkMap().saveAll();
            player.server.saveEverything(false, true, true);
            tag.putString("State", "stored");
            store.write(tag);
            SubLevelArchiveStore.reservePlots(container);
            throw err;
        }
    }

    // Recover the archived preview origin without exposing serialized plots to the client
    private static Vec3 previewAnchor(CompoundTag archive, ListTag bodies){
        ListTag preview = archive.getList("Preview", Tag.TAG_COMPOUND);
        if(preview.isEmpty()) return position(SubLevelSerializer.fromData(bodies.getCompound(0)).pose());
        CompoundTag first = preview.getCompound(0);
        for(int idx = 0; idx < bodies.size(); idx++){
            CompoundTag body = bodies.getCompound(idx);
            if(!body.getUUID("uuid").equals(first.getUUID("Body"))) continue;
            Vec3 center = SubLevelSerializer.fromData(body).pose()
                    .transformPosition(BlockPos.of(first.getLong("Pos")).getCenter());
            return center.subtract(first.getDouble("X") + 0.5D,
                    first.getDouble("Y") + 0.5D, first.getDouble("Z") + 0.5D);
        }
        throw new IllegalArgumentException("Archived preview does not match the stored bodies");
    }

    // Delete only an entirely unclaimed connected assembly after checking every body
    public static void deleteUnclaimed(ServerPlayer player, UUID rootId, Limits limits){
        ServerLevel level = player.serverLevel();
        checkThread(level);
        if(!player.server.isSingleplayerOwner(player.getGameProfile()) && !player.hasPermissions(2)) throw new IllegalArgumentException("Only the singleplayer owner or server operators may delete sublevels");
        var topology = SableAssemblyTopologyApi.discover(level, rootId);
        List<ServerSubLevel> bodies = topology.loadedBodies();
        if(!topology.available() || bodies.isEmpty() || bodies.size() > limits.maximumBodies()) throw new IllegalArgumentException("Assembly is unavailable or exceeds the body limit");
        for(ServerSubLevel body : bodies){
            if(ShipPermissionManager.get(player.server).owner(body.getUniqueId()) != null) throw new IllegalArgumentException("Claimed ships cannot be deleted");
            if(!WorldAccessPolicy.unclaimed(level, body.getUniqueId(), BlockPos.containing(position(body)))) throw new IllegalArgumentException("Claimed sublevels cannot be deleted");
            if(position(body).distanceToSqr(player.position()) > limits.locateRange() * limits.locateRange()) throw new IllegalArgumentException("Assembly is outside deletion range");
        }
        var container = SubLevelContainer.getContainer(level);
        for(ServerSubLevel body : bodies){
            body.getPlot().kickAllEntities();
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
    }

    public static Vec3 position(ServerSubLevel body){ return position(body.logicalPose()); }

    private static Vec3 position(dev.ryanhcode.sable.companion.math.Pose3d pose){
        return new Vec3(pose.position().x, pose.position().y, pose.position().z);
    }

    private static void checkThread(ServerLevel level){
        if(!level.getServer().isSameThread()) throw new IllegalStateException("Sublevel archives require the server thread");
    }

    public record Limits(int maximumArchives, int maximumBodies, int maximumBlocks, double storeRange, double locateRange){
        public Limits{
            if(maximumArchives < 1 || maximumBodies < 1 || maximumBlocks < 1 || storeRange < 1 || locateRange < 1) throw new IllegalArgumentException("Archive limits must be positive");
        }
    }
}
