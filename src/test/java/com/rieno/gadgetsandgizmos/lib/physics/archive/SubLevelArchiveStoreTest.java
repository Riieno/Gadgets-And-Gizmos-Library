package com.rieno.gadgetsandgizmos.lib.physics.archive;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubLevelArchiveStoreTest{
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    @Test void archivePersistsWithMetadataOnlyCatalogueAndConsumedMarker(@TempDir Path temp) throws Exception{
        var server = server(temp);
        var archive = SubLevelArchiveStore.forServer(server);
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        CompoundTag tag = record(id, owner);
        archive.write(tag);
        assertTrue(Files.exists(temp.resolve("gadgetsngizmos/sublevel_archives/" + id + ".nbt")));
        assertEquals(1, archive.entry(id).getList("Bodies", 10).size());
        var catalogue = archive.catalogue(owner, false);
        assertEquals(1, catalogue.size());
        assertFalse(catalogue.getFirst().contains("Bodies"));
        assertFalse(catalogue.getFirst().contains("Preview"));
        assertTrue(archive.catalogue(UUID.randomUUID(), false).isEmpty());

        var reopened = SubLevelArchiveStore.forServer(server(temp));
        assertEquals(1, reopened.entry(id).getList("Bodies", 10).size());
        reopened.consume(tag);
        assertTrue(reopened.catalogue(owner, false).isEmpty());
        assertFalse(SubLevelArchiveStore.forServer(server(temp)).entry(id).contains("Bodies"));
    }

    @Test void originalPlotSlotsStayReservedUntilArchiveIsConsumed(@TempDir Path temp) throws Exception{
        var server = server(temp);
        var archive = SubLevelArchiveStore.forServer(server);
        var tag = record(UUID.randomUUID(), UUID.randomUUID());
        archive.write(tag);
        var level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(server);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        var container = mock(ServerSubLevelContainer.class);
        when(container.getLevel()).thenReturn(level);
        when(container.getIndex(3, 4)).thenReturn(17);
        BitSet occupancy = new BitSet();
        when(container.getOccupancy()).thenReturn(occupancy);
        SubLevelArchiveStore.reservePlots(container);
        assertTrue(occupancy.get(17));
        archive.consume(tag);
        occupancy.clear();
        SubLevelArchiveStore.reservePlots(container);
        assertFalse(occupancy.get(17));
    }

    private static MinecraftServer server(Path path){
        var server = mock(MinecraftServer.class);
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(path);
        return server;
    }

    private static CompoundTag record(UUID id, UUID owner){
        var tag = new CompoundTag();
        tag.putUUID("Id", id); tag.putUUID("Owner", owner);
        tag.putString("State", "stored");
        tag.putString("Dimension", Level.OVERWORLD.location().toString());
        var body = new CompoundTag();
        var plot = new CompoundTag();
        plot.putInt("plot_x", 3); plot.putInt("plot_z", 4);
        body.put("plot", plot);
        var bodies = new ListTag(); bodies.add(body);
        tag.put("Bodies", bodies);
        var preview = new ListTag(); preview.add(new CompoundTag());
        tag.put("Preview", preview);
        return tag;
    }
}
