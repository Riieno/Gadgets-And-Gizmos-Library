package com.rieno.gadgetsandgizmos.lib.seat;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Discover seat blocks and count players mounted at their mapped positions
public final class MountedSeatRegistry {
    // Retain strictly named providers owned by their registering mods
    private static final Map<ResourceLocation, SeatProvider> PROVIDERS = new LinkedHashMap<>();

    // Prevent construction
    private MountedSeatRegistry() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Discover seats on loaded bodies without creating chunk tickets
    public static List<MappedSeat> discover(Collection<? extends SubLevel> bodies, int maximumBlocks){
        if(bodies == null || maximumBlocks <= 0) return List.of();
        List<MappedSeat> res = new ArrayList<>();
        int remaining = maximumBlocks;
        for(SubLevel body : bodies){
            if(body == null || body.isRemoved() || remaining <= 0) continue;
            for(var block : SubLevelBlockEntityCollector.getLoadedBlocks(body, remaining)){
                remaining--;
                BlockEntity blockEntity = block.state().hasBlockEntity()
                        ? SubLevelBlockEntityCollector.getBlockEntity(body, block.position()) : null;
                if(isSeat(body.getLevel(), block.position(), block.state(), blockEntity)){
                    res.add(new MappedSeat(body.getLevel(), block.position(), block.state(), blockEntity));
                }
            }
        }
        return res.stream().distinct().toList();
    }

    // Resolve a mount in either plot coordinates or its tracked body's world frame
    public static boolean isMountedAt(Level level, BlockPos pos, Entity mount){
        if(level == null || pos == null || mount == null || !mount.isAlive() || mount.level() != level) return false;
        if(pos.equals(mount.blockPosition())) return true;
        SubLevel body = SableLevelApi.containing(level, pos);
        if(body == null || SableLevelApi.containing(mount) != null || SableLevelApi.tracking(mount) != body) return false;
        Vec3 local = SableTransformApi.toLocalPosition(body, mount.position());
        return local != null && pos.equals(BlockPos.containing(local));
    }

    /** Register one strictly named seat provider owned by the caller's namespace. */
    public static synchronized void register(ResourceLocation id, SeatProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (PROVIDERS.containsKey(id)) {
            throw new IllegalStateException("Mounted seat provider already registered: " + id);
        }
        PROVIDERS.put(id, provider);
    }

    /** Get the registered provider ids in stable registration order. */
    public static synchronized List<ResourceLocation> providerIds() {
        return List.copyOf(PROVIDERS.keySet());
    }

    /** Check whether any provider recognizes one loaded block as a seat. */
    public static boolean isSeat(
            Level level,
            BlockPos position,
            BlockState state,
            @Nullable BlockEntity blockEntity
    ) {
        if (level == null || position == null || state == null || state.isAir()) return false;
        for (SeatProvider provider : providers()) {
            if (provider.isSeat(level, position, state, blockEntity)) return true;
        }
        return false;
    }

    /** Count unique players seated at a detached set of already mapped blocks. */
    public static Occupancy occupancy(
            Collection<MappedSeat> seats,
            Iterable<? extends Player> players
    ) {
        if (seats == null || seats.isEmpty()) return Occupancy.EMPTY;
        List<SeatProvider> providers = providers();
        Set<UUID> seatedPlayers = new LinkedHashSet<>();
        int occupiedSeats = 0;
        Set<MappedSeat> uniqueSeats = new LinkedHashSet<>(seats);
        int seatCount = 0;
        for (MappedSeat seat : uniqueSeats) {
            if (seat == null || seat.level() == null || seat.state() == null) continue;
            if(!seat.level().isLoaded(seat.position())) continue;
            BlockState state = seat.level().getBlockState(seat.position());
            List<SeatProvider> matching = providers.stream().filter(provider ->
                    provider.isSeat(seat.level(), seat.position(), state,
                            seat.blockEntity())).toList();
            if(matching.isEmpty()) continue;
            seatCount++;
            boolean occupied = matching.stream().anyMatch(provider ->
                    provider.isOccupied(seat.level(), seat.position(), state,
                            seat.blockEntity()));
            if (players != null) {
                for (Player player : players) {
                    if (player == null || !player.isAlive() || player instanceof FakePlayer || player.isSpectator()) continue;
                    for (SeatProvider provider : matching) {
                        if (provider.isPlayerSeatedAt(seat.level(), seat.position(), state,
                                seat.blockEntity(), player)) {
                            seatedPlayers.add(player.getUUID());
                            occupied = true;
                            break;
                        }
                    }
                }
            }
            if (occupied) occupiedSeats++;
        }
        return new Occupancy(seatCount, occupiedSeats, seatedPlayers.size());
    }

    private static synchronized List<SeatProvider> providers() {
        return new ArrayList<>(PROVIDERS.values());
    }

    /** Identify a seat and optionally specialize how its player occupant is resolved. */
    @FunctionalInterface
    public interface SeatProvider {
        boolean isSeat(Level level, BlockPos position, BlockState state,
                       @Nullable BlockEntity blockEntity);

        /** Check whether any entity mount at the seat currently has a passenger. */
        default boolean isOccupied(
                Level level,
                BlockPos position,
                BlockState state,
                @Nullable BlockEntity blockEntity
        ) {
            return level != null && position != null && !level.getEntities(
                    (Entity) null, new AABB(position),
                    entity -> !entity.getPassengers().isEmpty()).isEmpty();
        }

        /** Check the ordinary entity mount chain against the seat's level and block position. */
        default boolean isPlayerSeatedAt(
                Level level,
                BlockPos position,
                BlockState state,
                @Nullable BlockEntity blockEntity,
                Player player
        ) {
            Entity vehicle = player == null ? null : player.getVehicle();
            while (vehicle != null) {
                if (isMountedAt(level, position, vehicle)) return true;
                vehicle = vehicle.getVehicle();
            }
            return false;
        }
    }

    /** One seat block retained from a caller-owned vessel or assembly map. */
    public record MappedSeat(Level level, BlockPos position, BlockState state,
                             @Nullable BlockEntity blockEntity) {
        public MappedSeat {
            position = position == null ? BlockPos.ZERO : position.immutable();
        }
    }

    /** Current capacity and player occupancy for one mapped seat collection. */
    public record Occupancy(int seatCount, int occupiedSeatCount, int seatedPlayerCount) {
        public static final Occupancy EMPTY = new Occupancy(0, 0, 0);

        public Occupancy {
            seatCount = Math.max(0, seatCount);
            occupiedSeatCount = Math.max(0, Math.min(seatCount, occupiedSeatCount));
            seatedPlayerCount = Math.max(0, seatedPlayerCount);
        }

        /** Get the number of currently unoccupied mapped seats. */
        public int availableSeatCount() {
            return Math.max(0, seatCount - occupiedSeatCount);
        }
    }
}
