package com.rieno.gadgetsandgizmos.lib.discovery;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// Prepare terrain ahead of moving bodies without waiting for chunk generation
public final class MovingChunkResidency{
    private static final TicketType<UUID> PREFETCH_TICKET = TicketType.create(
            "gadgetsngizmos_moving_terrain", Comparator.<UUID>naturalOrder(), 40);
    private static final int MAX_CHUNKS = 256;
    private static final int REFRESH_TICKS = 20;
    private static final double LOOKAHEAD_SECONDS = 2.0D;
    private static final double MAX_LOOKAHEAD = 64.0D;
    private static final double TERRAIN_MARGIN = 16.0D;

    private MovingChunkResidency(){}

    // Describe a body's current world bounds and velocity in blocks per second
    public record Region(AABB bounds, Vec3 velocity){}

    // Create an independent terrain owner for one server level
    public static Lease lease(ServerLevel level){
        if(level == null) throw new IllegalArgumentException("A terrain lease requires a server level");
        return new Lease(level);
    }

    // Limit prediction and total residency even for oversized or invalid bodies
    static Set<ChunkPos> desiredChunks(Collection<Region> regions){
        Set<ChunkPos> desired = new LinkedHashSet<>();
        if(regions == null) return desired;
        for(Region region : regions){
            if(region == null || region.bounds() == null || region.velocity() == null) continue;
            AABB bounds = region.bounds();
            Vec3 velocity = region.velocity();
            if(!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.maxX)
                    || !Double.isFinite(bounds.minZ) || !Double.isFinite(bounds.maxZ)
                    || !Double.isFinite(velocity.x) || !Double.isFinite(velocity.z)) continue;
            if(Math.abs(velocity.x) <= 0.05D && Math.abs(velocity.z) <= 0.05D) continue;
            double dx = Mth.clamp(velocity.x * LOOKAHEAD_SECONDS, -MAX_LOOKAHEAD, MAX_LOOKAHEAD);
            double dz = Mth.clamp(velocity.z * LOOKAHEAD_SECONDS, -MAX_LOOKAHEAD, MAX_LOOKAHEAD);
            int minX = Mth.floor(bounds.minX + Math.min(0.0D, dx) - TERRAIN_MARGIN) >> 4;
            int maxX = Mth.floor(bounds.maxX + Math.max(0.0D, dx) + TERRAIN_MARGIN) >> 4;
            int minZ = Mth.floor(bounds.minZ + Math.min(0.0D, dz) - TERRAIN_MARGIN) >> 4;
            int maxZ = Mth.floor(bounds.maxZ + Math.max(0.0D, dz) + TERRAIN_MARGIN) >> 4;
            long count = ((long)maxX - minX + 1L) * ((long)maxZ - minZ + 1L);
            if(count <= 0 || count > MAX_CHUNKS) continue;
            for(int x = minX; x <= maxX; x++){
                for(int z = minZ; z <= maxZ; z++){
                    desired.add(new ChunkPos(x, z));
                    if(desired.size() == MAX_CHUNKS) return desired;
                }
            }
        }
        return desired;
    }

    // Own only temporary full-chunk tickets; Sable controls physics and ticking
    public static final class Lease implements AutoCloseable{
        private final ServerLevel level;
        private final UUID owner = UUID.randomUUID();
        private Set<ChunkPos> retained = Set.of();
        private long lastRefresh = Long.MIN_VALUE;
        private boolean closed;

        private Lease(ServerLevel level){
            this.level = level;
        }

        // Request new terrain and release only this owner's previous footprint
        public void synchronize(Collection<Region> regions){
            if(closed) return;
            Set<ChunkPos> desired = desiredChunks(regions);
            long tick = level.getGameTime();
            boolean refresh = lastRefresh == Long.MIN_VALUE || tick < lastRefresh
                    || tick - lastRefresh >= REFRESH_TICKS;
            var chunks = level.getChunkSource();
            for(ChunkPos pos : desired){
                if(refresh || !retained.contains(pos)) chunks.addRegionTicket(PREFETCH_TICKET, pos, 0, owner);
            }
            for(ChunkPos pos : retained){
                if(!desired.contains(pos)) chunks.removeRegionTicket(PREFETCH_TICKET, pos, 0, owner);
            }
            retained = desired;
            if(refresh) lastRefresh = tick;
        }

        // Release terrain without removing another ship, player or loader's tickets
        @Override public void close(){
            if(closed) return;
            var chunks = level.getChunkSource();
            for(ChunkPos pos : retained) chunks.removeRegionTicket(PREFETCH_TICKET, pos, 0, owner);
            retained = Set.of();
            closed = true;
        }
    }
}
