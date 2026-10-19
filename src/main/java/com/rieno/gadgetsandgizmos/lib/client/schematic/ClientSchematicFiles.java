package com.rieno.gadgetsandgizmos.lib.client.schematic;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicClientFiles;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicFiles;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicCatalogue;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicProvenance;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematic;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

// Discover private client files and pace uploads without blocking the render thread
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID, value = Dist.CLIENT)
public final class ClientSchematicFiles{
    // Retain only the local catalogue and the one transfer currently requested by the client
    private static List<Entry> files = List.of();
    private static CompletableFuture<List<Entry>> scan;
    private static Transfer transfer;
    private static String error = "";
    private static boolean initialized;
    private static int maximumBodies = 128;
    private static int maximumBlocks = 262144;

    private ClientSchematicFiles(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the current player's schematic folder independently of the server's files
    public static Path directory(){
        return Minecraft.getInstance().gameDirectory.toPath().resolve("schematics").toAbsolutePath().normalize();
    }

    // Create the player folder if needed and open it through the operating system
    public static void openDirectory(){
        try{
            Path dir = directory();
            Files.createDirectories(dir);
            Util.getPlatform().openFile(dir.toFile());
            error = "";
        }catch(IOException | RuntimeException err){ error = "Cannot open schematic directory: " + message(err); }
    }

    // Read local schematic folders in the background when the browser first opens
    public static List<Entry> catalogue(){
        if(!initialized) refresh();
        if(scan != null && scan.isDone()){
            try{ files = scan.join(); }
            catch(RuntimeException err){ error = message(err); files = List.of(); }
            scan = null;
        }
        return files;
    }

    // Apply the connected server's limits before returning private compatible files
    public static List<Entry> catalogue(int bodies, int blocks){
        if(bodies < 1 || blocks < 1) return List.of();
        if(maximumBodies != bodies || maximumBlocks != blocks){
            maximumBodies = bodies; maximumBlocks = blocks;
            if(scan != null) scan.cancel(false);
            scan = null; files = List.of(); initialized = false;
        }
        return catalogue();
    }

    // Refresh Create, Photomancy and Toolgun local folders without publishing their names
    public static void refresh(){
        if(scan != null && !scan.isDone()) return;
        initialized = true;
        error = "";
        var minecraft = Minecraft.getInstance();
        if(minecraft.level == null){ files = List.of(); return; }
        Path root = minecraft.gameDirectory.toPath();
        var registry = minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK);
        int bodies = maximumBodies, blocks = maximumBlocks;
        scan = CompletableFuture.supplyAsync(() -> {
            List<Entry> res = new ArrayList<>();
            for(String folder : List.of("schematics", "Sable-Schematics", "enxv_aeronautics_structures")){
                Path dir = root.resolve(folder);
                String prefix = "client:" + folder + "/";
                try{
                    for(var inspected : SchematicCatalogue.catalogue(dir, prefix, registry, bodies, blocks)){
                        var file = inspected.file();
                        res.add(new Entry(file.id(), folder + "/" + file.name(), dir, prefix, inspected.format(), inspected.source()));
                    }
                }catch(IOException err){ throw new IllegalArgumentException("Cannot read " + folder + ": " + err.getMessage(), err); }
            }
            return List.copyOf(res);
        });
    }

    // Identify a file that belongs to this client rather than the server catalogue
    public static boolean contains(UUID id){ return catalogue().stream().anyMatch(row -> row.id().equals(id)); }

    // Expose loading and transfer progress to the consuming screen
    public static String status(){
        if(!error.isBlank()) return error;
        if(transfer != null) return transfer.data == null ? "Reading client schematic"
                : "Uploading client schematic " + (transfer.offset * 100L / transfer.data.length) + "%";
        return scan != null ? "Reading client schematic folders" : "";
    }

    // Upload one selected file through a consuming mod's authenticated packet bridge
    public static void upload(UUID id, Consumer<Chunk> send, Consumer<Exception> failure){
        Entry file = catalogue().stream().filter(row -> row.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Client schematic is unavailable; refresh the list"));
        if(transfer != null) throw new IllegalArgumentException("Wait for the client schematic upload to finish");
        error = "";
        transfer = new Transfer(id, CompletableFuture.supplyAsync(() -> {
            try{ return SubLevelSchematicFiles.read(file.directory(), file.idPrefix(),
                    new SubLevelSchematicFiles.Entry(id, file.name().substring(file.name().indexOf('/') + 1))); }
            catch(IOException err){ throw new IllegalArgumentException(err.getMessage(), err); }
        }), send, failure);
    }

    // Send a bounded amount each client tick and report file or connection failures
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post evt){
        Transfer next = transfer;
        if(next == null) return;
        if(Minecraft.getInstance().getConnection() == null){ clear(); return; }
        try{
            if(next.data == null){
                if(!next.read.isDone()) return;
                next.data = next.read.join();
                if(next.data.length == 0) throw new IllegalArgumentException("Client schematic is empty");
            }
            for(int idx = 0; idx < 4 && next.offset < next.data.length; idx++){
                int end = Math.min(next.data.length, next.offset + SchematicClientFiles.CHUNK_BYTES);
                next.send.accept(new Chunk(next.id, next.data.length, next.offset, Arrays.copyOfRange(next.data, next.offset, end)));
                next.offset = end;
            }
            if(next.offset == next.data.length) transfer = null;
        }catch(Exception err){
            transfer = null;
            error = message(err);
            next.failure.accept(new IllegalArgumentException(error, err));
        }
    }

    // Drop pending transfers and catalogue errors when leaving a server
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut evt){ clear(); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Release client state without retaining bytes from a previous connection
    private static void clear(){
        if(scan != null) scan.cancel(false);
        if(transfer != null) transfer.read.cancel(false);
        transfer = null; scan = null; files = List.of(); initialized = false; error = "";
    }

    // Display the actual file failure rather than its asynchronous wrapper
    private static String message(Throwable err){
        while(err.getCause() != null) err = err.getCause();
        return err.getMessage() == null ? "Client schematic operation failed" : err.getMessage();
    }

    // Identify one private local file without sending its path to a server
    public record Entry(UUID id, String name, Path directory, String idPrefix, SubLevelSchematic.Format format, ResourceLocation source){
        public Entry(UUID id, String name, Path directory, String idPrefix, SubLevelSchematic.Format format){
            this(id, name, directory, idPrefix, format, SchematicProvenance.source(new net.minecraft.nbt.CompoundTag(), format));
        }
        // Preserve callers which supply a native private schematic entry
        public Entry(UUID id, String name, Path directory, String idPrefix){
            this(id, name, directory, idPrefix, SubLevelSchematic.Format.NATIVE);
        }
    }

    // Carry one bounded byte slice to the consuming mod's network protocol
    public record Chunk(UUID id, int total, int offset, byte[] data){
        public Chunk{ data = data.clone(); }
        @Override public byte[] data(){ return data.clone(); }
    }

    // Retain asynchronous disk reads and the packet bridge on the client thread
    private static final class Transfer{
        private final UUID id;
        private final CompletableFuture<byte[]> read;
        private final Consumer<Chunk> send;
        private final Consumer<Exception> failure;
        private byte[] data;
        private int offset;

        private Transfer(UUID id, CompletableFuture<byte[]> read, Consumer<Chunk> send, Consumer<Exception> failure){
            this.id = id; this.read = read; this.send = send; this.failure = failure;
        }
    }
}
