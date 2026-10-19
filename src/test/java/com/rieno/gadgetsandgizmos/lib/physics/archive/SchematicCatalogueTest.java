package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaterniond;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Exercise content-based compatibility filtering before files enter either browser catalogue
class SchematicCatalogueTest{
    @TempDir Path directory;


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the block registry used by the real schematic decoder
    @BeforeAll static void prepareRegistries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    // Hide bad bytes, entity templates, unsupported formats and files with unrelated extensions
    @Test void incompatibleFilesNeverEnterTheCatalogue() throws Exception{
        NbtIo.writeCompressed(nativeTag(), directory.resolve("assembly.nbt"));
        Files.writeString(directory.resolve("broken.nbt"), "invalid bytes");
        NbtIo.writeCompressed(nativeTag(), directory.resolve("unrelated.txt"));
        CompoundTag entities = nativeTag();
        ListTag list = new ListTag(); list.add(new CompoundTag()); entities.put("entities", list);
        NbtIo.writeCompressed(entities, directory.resolve("entities.nbt"));
        CompoundTag unsupported = new CompoundTag(); unsupported.putString("format", "enxv_aeronautics_plot_print_v1");
        NbtIo.writeCompressed(unsupported, directory.resolve("old.excraft"));
        NbtIo.writeCompressed(new CompoundTag(), directory.resolve("empty.nbt"));
        var entries = SchematicCatalogue.catalogue(directory, "", BuiltInRegistries.BLOCK.asLookup(), 8, 64);
        assertEquals(List.of("assembly.nbt"), entries.stream().map(row -> row.file().name()).toList());
        assertEquals(SubLevelSchematic.Format.NATIVE, entries.getFirst().format());
    }

    // Refresh cached verdicts when file content or the server's block limits change
    @Test void changedFilesAndLimitsAreRevalidated() throws Exception{
        Path file = directory.resolve("assembly.nbt");
        NbtIo.writeCompressed(nativeTag(), file);
        var registry = BuiltInRegistries.BLOCK.asLookup();
        assertEquals(1, SchematicCatalogue.catalogue(directory, "", registry, 8, 64).size());
        assertTrue(SchematicCatalogue.catalogue(directory, "", registry, 8, 1).isEmpty());
        Files.writeString(file, "replaced with incompatible content");
        assertTrue(SchematicCatalogue.catalogue(directory, "", registry, 8, 64).isEmpty());
        NbtIo.writeCompressed(nativeTag(), file);
        assertEquals(1, SchematicCatalogue.catalogue(directory, "", registry, 8, 64).size());
    }

    // Detect Photomancy from its actual blueprint data even when it uses an ordinary NBT extension
    @Test void sourceFormatsComeFromDecodedContent() throws Exception{
        CompoundTag tag = new CompoundTag(), body = new CompoundTag(); tag.putInt("version", 1);
        body.putUUID("source_uuid", UUID.randomUUID()); body.putInt("id", 1);
        ListTag palette = new ListTag(); palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState())); body.put("block_palette", palette);
        CompoundTag bounds = new CompoundTag();
        for(String axis : List.of("x", "y", "z")){ bounds.putInt("min_" + axis, 0); bounds.putInt("max_" + axis, 0); }
        body.put("local_bounds", bounds);
        CompoundTag pose = new CompoundTag(), pos = new CompoundTag(), turn = new CompoundTag();
        for(String axis : List.of("x", "y", "z")){ pos.putDouble(axis, 0); turn.putDouble(axis, 0); }
        turn.putDouble("w", 1); pose.put("position", pos); pose.put("orientation", turn); body.put("relative_pose", pose);
        CompoundTag block = new CompoundTag(), local = new CompoundTag();
        for(String axis : List.of("x", "y", "z")) local.putInt(axis, 0);
        block.put("local_pos", local); block.putInt("palette_id", 0);
        ListTag blocks = new ListTag(); blocks.add(block); body.put("blocks", blocks);
        ListTag bodies = new ListTag(); bodies.add(body); tag.put("sub_levels", bodies);
        NbtIo.writeCompressed(tag, directory.resolve("blueprint.nbt"));
        var entries = SchematicCatalogue.catalogue(directory, "", BuiltInRegistries.BLOCK.asLookup(), 8, 64);
        assertEquals(1, entries.size());
        assertEquals(SubLevelSchematic.Format.PHOTOMANCY, entries.getFirst().format());
    }

    // Accept a supported Toolgun plot while keeping native files in their content-defined category
    @Test void toolgunAndNativeFormatsRemainDistinct() throws Exception{
        CompoundTag tag = new CompoundTag(), body = new CompoundTag(), pos = new CompoundTag();
        UUID id = UUID.randomUUID();
        tag.putString("format", "enxv_aeronautics_plot_print_v9"); tag.putUUID("root_sublevel", id);
        tag.putInt("source_min_build_height", 0); body.putUUID("sublevel_id", id);
        for(String axis : List.of("x", "y", "z")) pos.putDouble(axis, 0);
        body.put("relative_position", pos);
        CompoundTag plot = new CompoundTag(), chunks = new CompoundTag(), chunk = new CompoundTag();
        CompoundTag sections = new CompoundTag(), section = new CompoundTag(), states = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
        long[] packed = new long[256]; packed[0] = 1;
        states.put("palette", palette); states.putLongArray("data", packed);
        section.put("block_states", states); sections.put("0", section); chunk.put("sections", sections);
        chunks.put("0", chunk); plot.put("chunks", chunks); body.put("plot", plot);
        ListTag bodies = new ListTag(); bodies.add(body); tag.put("sublevels", bodies);
        NbtIo.writeCompressed(tag, directory.resolve("toolgun.nbt"));
        NbtIo.writeCompressed(nativeTag(), directory.resolve("native.excraft"));
        var entries = SchematicCatalogue.catalogue(directory, "", BuiltInRegistries.BLOCK.asLookup(), 8, 64);
        assertEquals(2, entries.size());
        assertEquals(SubLevelSchematic.Format.TOOLGUN, entries.stream().filter(row -> row.file().name().equals("toolgun.nbt"))
                .findFirst().orElseThrow().format());
        assertEquals(SubLevelSchematic.Format.NATIVE, entries.stream().filter(row -> row.file().name().equals("native.excraft"))
                .findFirst().orElseThrow().format());
    }

    // Build a small real native schematic that exercises the normal decoder and limit checks
    @Test void nativeLayoutRequiresAnExplicitProducerTag() throws Exception{
        ResourceLocation producer = ResourceLocation.fromNamespaceAndPath("createthrusters", "digisable");
        CompoundTag unknown = nativeTag();
        NbtIo.writeCompressed(unknown, directory.resolve("unknown.nbt"));
        SubLevelSchematic schematic = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), unknown, 8, 64);
        NbtIo.writeCompressed(SubLevelSchematicFiles.encode(schematic, producer), directory.resolve("tablet.nbt"));
        var rows = SchematicCatalogue.catalogue(directory, "", BuiltInRegistries.BLOCK.asLookup(), 8, 64);
        assertEquals(SchematicProvenance.UNKNOWN, rows.stream().filter(row -> row.file().name().equals("unknown.nbt"))
                .findFirst().orElseThrow().source());
        assertEquals(producer, rows.stream().filter(row -> row.file().name().equals("tablet.nbt"))
                .findFirst().orElseThrow().source());
        unknown.putString(SchematicProvenance.SOURCE_TAG, "invalid source!");
        assertEquals(SchematicProvenance.UNKNOWN, SchematicProvenance.source(unknown, SubLevelSchematic.Format.NATIVE));
        assertEquals(SchematicProvenance.PHOTOMANCY, SchematicProvenance.source(unknown, SubLevelSchematic.Format.PHOTOMANCY));
        assertEquals(SchematicProvenance.TOOLGUN, SchematicProvenance.source(unknown, SubLevelSchematic.Format.TOOLGUN));
    }

    // Build a small real native schematic that exercises the normal decoder and limit checks
    private static CompoundTag nativeTag(){
        return SubLevelSchematicFiles.encode(new SubLevelSchematic(List.of(new SubLevelSchematic.Body(
                UUID.randomUUID(), Vec3.ZERO, new Quaterniond(), new BlockPos(2, 1, 1), List.of(
                new SubLevelSchematic.Block(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), new CompoundTag()),
                new SubLevelSchematic.Block(new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState(), new CompoundTag()))))));
    }
}
