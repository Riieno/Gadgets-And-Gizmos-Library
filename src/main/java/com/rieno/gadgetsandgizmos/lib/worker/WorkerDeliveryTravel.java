package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;

// Bring distant deliveries to the edge of view and retain their return journey
public final class WorkerDeliveryTravel{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final TicketType<UUID> TRAVEL_TICKET = TicketType.create(
            "gadgetsngizmos_worker_delivery", Comparator.comparing(UUID::toString), 100);
    private final UUID recipient;
    private final Vec3 home;
    private Vec3 boundary;
    private boolean outbound;
    private boolean returning;
    private WorkerPathing.LiveNavigator navigation;
    private ChunkPos retainedChunk;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain the local departure point and recipient across saves
    private WorkerDeliveryTravel(UUID recipient, Vec3 home){
        this.recipient = recipient;
        this.home = home;
    }

    // Start travel only for a player farther than the configured delivery threshold
    public static WorkerDeliveryTravel begin(Entity worker, ServerPlayer player, double threshold){
        if(worker.level() != player.level() || worker.distanceToSqr(player) <= threshold * threshold) return null;
        return new WorkerDeliveryTravel(player.getUUID(), worker.position());
    }

    // Walk out of view after delivering before returning to the departure area
    public void returnHome(){
        returning = true;
        navigation = null;
        boundary = null;
    }

    // Recover a queued worker whose entity chunk has not loaded with its station
    public static void retainPosition(ServerLevel level, UUID workerId, BlockPos pos){
        level.getChunkSource().addRegionTicket(TRAVEL_TICKET, new ChunkPos(pos), 2, workerId);
        if(!level.isLoaded(pos)) level.getChunk(pos);
    }

    // Keep the pod and worker ticking while preparing or following the distant journey
    public State advance(ServerLevel level, Entity worker, BlockPos station, boolean allowFlight){
        retain(level, worker, station);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(recipient);
        if(player == null || player.serverLevel() != level){
            return teleportHome(level, worker);
        }
        if(!outbound){
            boundary = boundaryPosition(level, worker, player, home);
            if(boundary == null) return State.WAITING;
            worker.teleportTo(boundary.x, boundary.y, boundary.z);
            worker.setDeltaMovement(Vec3.ZERO);
            outbound = true;
            retain(level, worker, station);
        }
        if(!returning) return State.DELIVERING;
        if(outsideView(worker.position(), player)) return teleportHome(level, worker);
        if(boundary == null || !outsideView(boundary, player)){
            boundary = boundaryPosition(level, worker, player, home);
            navigation = null;
        }
        if(boundary == null) return State.WAITING;
        if(navigation == null) navigation = WorkerPathing.liveNavigator(1024.0D, allowFlight);
        WorkerPathing.NavigationStep step = navigation.advance(level, worker.position(), boundary, 96, 0.18D);
        if(step.unavailable()){
            boundary = null;
            navigation = null;
            return State.WAITING;
        }
        Vec3 movement = step.position().subtract(worker.position());
        if(movement.lengthSqr() > 1.0E-9D){
            worker.setYRot((float) Math.toDegrees(Math.atan2(-movement.x, movement.z)));
            worker.setPos(step.position());
            worker.setDeltaMovement(Vec3.ZERO);
        }
        return State.RETURNING;
    }

    // Use the effective client and server view radius with a margin beyond the last visible chunk
    public static double viewBoundary(int clientChunks, int serverChunks){
        return Math.max(2, Math.min(clientChunks, serverChunks)) * 16.0D + 32.0D;
    }

    // Check the square chunk view instead of a circle that leaves visible corner chunks
    private static boolean outsideView(Vec3 pos, ServerPlayer player){
        double distance = viewBoundary(player.requestedViewDistance(), player.server.getPlayerList().getViewDistance());
        return Math.max(Math.abs(pos.x - player.getX()), Math.abs(pos.z - player.getZ())) >= distance;
    }

    // Search supported positions around the view boundary without placing a worker inside blocks
    private static Vec3 boundaryPosition(ServerLevel level, Entity worker, ServerPlayer player, Vec3 home){
        double distance = viewBoundary(player.requestedViewDistance(), level.getServer().getPlayerList().getViewDistance()) + 8.0D;
        double heading = Math.atan2(home.z - player.getZ(), home.x - player.getX());
        for(int idx = 0; idx < 16; idx++){
            double angle = heading + idx * Math.PI / 8.0D;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double scale = distance / Math.max(Math.abs(dx), Math.abs(dz));
            BlockPos column = BlockPos.containing(player.getX() + dx * scale, player.getY(), player.getZ() + dz * scale);
            if(!level.getWorldBorder().isWithinBounds(column)) continue;
            level.getChunk(column);
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            for(int offset = 0; offset <= 32; offset++){
                int y = offset == 32 ? surface : column.getY() + (offset % 2 == 0 ? offset / 2 : -(offset + 1) / 2);
                Vec3 pos = WorkerPathing.standingPosition(level, new BlockPos(column.getX(), y, column.getZ()));
                if(pos != null && level.noCollision(worker, worker.getBoundingBox().move(pos.subtract(worker.position())))) return pos;
            }
        }
        return null;
    }

    // Renew expiring tickets and release the previous worker chunk after movement
    private void retain(ServerLevel level, Entity worker, BlockPos station){
        ChunkPos next = worker.chunkPosition();
        if(retainedChunk != null && !retainedChunk.equals(next)){
            level.getChunkSource().removeRegionTicket(TRAVEL_TICKET, retainedChunk, 2, worker.getUUID());
        }
        level.getChunkSource().addRegionTicket(TRAVEL_TICKET, new ChunkPos(station), 2, worker.getUUID());
        level.getChunkSource().addRegionTicket(TRAVEL_TICKET, new ChunkPos(BlockPos.containing(home)), 2, worker.getUUID());
        level.getChunkSource().addRegionTicket(TRAVEL_TICKET, next, 2, worker.getUUID());
        retainedChunk = next;
    }

    // Restore the worker only after the departure area is loaded and clear
    private State teleportHome(ServerLevel level, Entity worker){
        level.getChunk(BlockPos.containing(home));
        Vec3 pos = WorkerPathing.reachableInteractionPosition(level,
                java.util.List.of(BlockPos.containing(home).below()), null, home);
        if(pos == null) return State.WAITING;
        worker.teleportTo(pos.x, pos.y, pos.z);
        worker.setDeltaMovement(Vec3.ZERO);
        return State.HOME;
    }

    // Persist travel independently of temporary chunk tickets and path searches
    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Recipient", recipient);
        tag.putDouble("HomeX", home.x);
        tag.putDouble("HomeY", home.y);
        tag.putDouble("HomeZ", home.z);
        tag.putBoolean("Outbound", outbound);
        tag.putBoolean("Returning", returning);
        return tag;
    }

    // Restore a delivery journey after the worker and its pod reload
    public static WorkerDeliveryTravel fromTag(CompoundTag tag){
        if(!tag.hasUUID("Recipient")) return null;
        WorkerDeliveryTravel travel = new WorkerDeliveryTravel(tag.getUUID("Recipient"),
                new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ")));
        travel.outbound = tag.getBoolean("Outbound");
        travel.returning = tag.getBoolean("Returning");
        return travel;
    }

    public enum State{
        WAITING, DELIVERING, RETURNING, HOME
    }
}
