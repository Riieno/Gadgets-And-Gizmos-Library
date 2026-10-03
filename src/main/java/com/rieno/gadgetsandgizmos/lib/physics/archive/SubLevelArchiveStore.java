package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Keep durable server archives separate from item NBT and reserve their original plot addresses
public final class SubLevelArchiveStore{
    private static final Map<MinecraftServer, SubLevelArchiveStore> STORES = new WeakHashMap<>();
    private final Path folder;
    private final Map<UUID, CompoundTag> entries = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private SubLevelArchiveStore(MinecraftServer server) throws IOException{
        folder = server.getWorldPath(LevelResource.ROOT).resolve("gadgetsngizmos/sublevel_archives");
        Files.createDirectories(folder);
        try(var paths = Files.list(folder)){
            for(Path path : paths.filter(path -> path.getFileName().toString().endsWith(".nbt")).toList()){
                CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.create(128L * 1024L * 1024L));
                if(tag.hasUUID("Id")) entries.put(tag.getUUID("Id"), tag);
            }
        }
    }

    // Open one world's catalogue, including unfinished transactions from an earlier session
    public static synchronized SubLevelArchiveStore forServer(MinecraftServer server) throws IOException{
        SubLevelArchiveStore store = STORES.get(server);
        if(store == null){
            store = new SubLevelArchiveStore(server);
            STORES.put(server, store);
        }
        return store;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public CompoundTag entry(UUID id){
        CompoundTag tag = entries.get(id);
        return tag == null ? new CompoundTag() : tag.copy();
    }

    // Return metadata without exposing the serialized inventories to clients
    public List<CompoundTag> catalogue(UUID owner, boolean operator){
        List<CompoundTag> rows = new ArrayList<>();
        for(CompoundTag tag : entries.values()){
            if("consumed".equals(tag.getString("State"))) continue;
            if(!operator && !owner.equals(tag.getUUID("Owner"))) continue;
            CompoundTag row = metadata(tag);
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    // Replace a transaction file only after its complete compressed data reaches disk
    public void write(CompoundTag tag) throws IOException{
        UUID id = tag.getUUID("Id");
        Path target = folder.resolve(id + ".nbt");
        Path temp = Files.createTempFile(folder, id + "-", ".tmp");
        try{
            NbtIo.writeCompressed(tag, temp);
            try(FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)){ channel.force(true); }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            entries.put(id, tag.copy());
        }finally{ Files.deleteIfExists(temp); }
    }

    // Preserve consumed markers so a crash cannot turn a completed extraction into a second copy
    public void consume(CompoundTag tag) throws IOException{
        CompoundTag marker = metadata(tag);
        marker.putString("State", "consumed");
        marker.remove("Bodies");
        marker.remove("Preview");
        write(marker);
    }

    private static CompoundTag metadata(CompoundTag tag){
        CompoundTag res = new CompoundTag();
        for(String key : tag.getAllKeys()){
            if(!"Bodies".equals(key) && !"Preview".equals(key)) res.put(key, tag.get(key).copy());
        }
        return res;
    }

    // Protect stored addresses from Sable's automatic plot allocator
    public static void reservePlots(ServerSubLevelContainer container){
        try{
            SubLevelArchiveStore store = forServer(container.getLevel().getServer());
            for(CompoundTag tag : store.entries.values()){
                if("consumed".equals(tag.getString("State"))
                        || !container.getLevel().dimension().location().toString().equals(tag.getString("Dimension"))) continue;
                ListTag bodies = tag.getList("Bodies", Tag.TAG_COMPOUND);
                for(int idx = 0; idx < bodies.size(); idx++){
                    CompoundTag plot = bodies.getCompound(idx).getCompound("plot");
                    container.getOccupancy().set(container.getIndex(plot.getInt("plot_x"), plot.getInt("plot_z")));
                }
            }
        }catch(IOException err){ throw new IllegalStateException("Cannot reserve archived sublevel plots", err); }
    }
}
