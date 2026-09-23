package com.rieno.gadgetsandgizmos.lib.seat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
    private static final Map<ResourceLocation, SeatProvider> PROVIDERS = new LinkedHashMap<>();

    private MountedSeatRegistry() {
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
        for (MappedSeat seat : seats) {
            if (seat == null || seat.level() == null || seat.state() == null) continue;
            List<SeatProvider> matching = providers.stream().filter(provider ->
                    provider.isSeat(seat.level(), seat.position(), seat.state(),
                            seat.blockEntity())).toList();
            boolean occupied = matching.stream().anyMatch(provider ->
                    provider.isOccupied(seat.level(), seat.position(), seat.state(),
                            seat.blockEntity()));
            if (players != null) {
                for (Player player : players) {
                    if (player == null || !player.isAlive()) continue;
                    for (SeatProvider provider : matching) {
                        if (provider.isPlayerSeatedAt(seat.level(), seat.position(), seat.state(),
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
        return new Occupancy(seats.size(), occupiedSeats, seatedPlayers.size());
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
                if (vehicle.level() == level && position.equals(vehicle.blockPosition())) return true;
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
