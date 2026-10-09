package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Retain bounded client uploads privately for their authenticated owner
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID)
public final class SchematicClientFiles{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int MAXIMUM_BYTES = 64 * 1024 * 1024;
    public static final int CHUNK_BYTES = 32 * 1024;
    private static final long SERVER_BYTES = 128L * 1024 * 1024;
    private static final long TIMEOUT_TICKS = 20 * 60;
    // Scope private selections and incomplete uploads to one server and one player
    private static final Map<MinecraftServer, Map<UUID, Uploads>> FILES = new WeakHashMap<>();

    private SchematicClientFiles(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Receive ordered chunks after the consuming mod has checked its feature permissions
    public static boolean receive(MinecraftServer server, UUID owner, UUID id, int total, int offset, byte[] data){
        requireThread(server);
        if(owner == null || id == null || total < 1 || total > MAXIMUM_BYTES || offset < 0
                || data == null || data.length < 1 || data.length > CHUNK_BYTES || offset > total - data.length)
            throw new IllegalArgumentException("Invalid schematic upload chunk");
        Map<UUID, Uploads> files = FILES.computeIfAbsent(server, val -> new HashMap<>());
        Uploads uploads = files.computeIfAbsent(owner, val -> new Uploads());
        Selection selection = uploads.pending;
        if(offset == 0){
            long used = files.entrySet().stream().mapToLong(row -> {
                Uploads val = row.getValue();
                return (val.selected == null ? 0 : val.selected.bytes.length)
                        + (row.getKey().equals(owner) || val.pending == null ? 0 : val.pending.bytes.length);
            }).sum();
            if(total > SERVER_BYTES - used) throw new IllegalArgumentException("Server schematic upload capacity is full");
            selection = new Selection(id, new byte[total]);
            uploads.pending = selection;
        }
        if(selection == null || !selection.id.equals(id) || selection.bytes.length != total || selection.received != offset)
            throw new IllegalArgumentException("Schematic upload is incomplete; select the file again");
        System.arraycopy(data, 0, selection.bytes, offset, data.length);
        selection.received += data.length;
        selection.lastTick = server.getTickCount();
        if(selection.received != total) return false;
        uploads.selected = selection;
        uploads.pending = null;
        return true;
    }

    // Resolve a private file only for the player who uploaded it
    public static boolean contains(MinecraftServer server, UUID owner, UUID id){
        requireThread(server);
        Uploads uploads = FILES.getOrDefault(server, Map.of()).get(owner);
        Selection selection = uploads == null ? null : uploads.selected;
        return selection != null && selection.id.equals(id) && selection.received == selection.bytes.length;
    }

    // Apply the same decoder and safe block entity policy as shared server files
    public static SubLevelSchematic load(ServerLevel level, UUID owner, UUID id, int maximumBodies, int maximumBlocks) throws IOException{
        MinecraftServer server = level.getServer();
        if(!contains(server, owner, id)) throw new IllegalArgumentException("Client schematic is unavailable; select it again");
        Selection selection = FILES.get(server).get(owner).selected;
        return SubLevelSchematicFiles.sanitize(level, SubLevelSchematicFiles.decode(
                server.registryAccess().lookupOrThrow(Registries.BLOCK), selection.bytes, maximumBodies, maximumBlocks));
    }

    // Release uploaded files when their player disconnects
    public static void remove(MinecraftServer server, UUID owner){
        requireThread(server);
        Map<UUID, Uploads> files = FILES.get(server);
        if(files != null) files.remove(owner);
    }

    // Cancel an incomplete transfer while keeping the player's ready file available
    public static void cancelUpload(MinecraftServer server, UUID owner){
        requireThread(server);
        Uploads uploads = FILES.getOrDefault(server, Map.of()).get(owner);
        if(uploads != null) uploads.pending = null;
    }

    // Expire incomplete transfers without affecting the currently selected complete file
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post evt){
        if(evt.getServer().getTickCount() % 20 != 0) return;
        Map<UUID, Uploads> files = FILES.get(evt.getServer());
        if(files != null) files.values().removeIf(row -> {
            if(row.pending != null && (long) evt.getServer().getTickCount() - row.pending.lastTick > TIMEOUT_TICKS)
                row.pending = null;
            return row.selected == null && row.pending == null;
        });
    }

    // Clear private bytes as soon as their owning player leaves the server
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent evt){
        if(evt.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) remove(player.server, player.getUUID());
    }

    // Drop every retained upload from a stopped server
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent evt){ FILES.remove(evt.getServer()); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep upload state on the owning server thread
    private static void requireThread(MinecraftServer server){
        if(!server.isSameThread()) throw new IllegalStateException("Schematic uploads require the server thread");
    }

    // Retain the ready selection until a replacement transfer finishes
    private static final class Uploads{
        private Selection selected;
        private Selection pending;
    }

    // Keep one bounded selected file or incomplete transfer
    private static final class Selection{
        private final UUID id;
        private final byte[] bytes;
        private int received;
        private long lastTick;

        private Selection(UUID id, byte[] bytes){ this.id = id; this.bytes = bytes; }
    }
}
