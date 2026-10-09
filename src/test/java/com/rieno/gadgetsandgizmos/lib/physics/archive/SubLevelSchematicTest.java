package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.worker.WorkerInventoryEndpoint;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import dev.ryanhcode.sable.util.SableNBTUtils;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Verify file boundaries, assembly geometry and real material custody
class SubLevelSchematicTest{
    private static RegistryAccess registries;

    // Initialize vanilla registries for real block states and item components
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    // Preserve connected body identities, safe block data and arbitrary body orientation
    @Test void nativeSchematicRoundTripKeepsConnectedGeometry(){
        var schematic = template();
        var loaded = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), SubLevelSchematicFiles.encode(schematic), 8, 16);
        assertEquals(schematic.bodies().getFirst().id(), loaded.bodies().getFirst().id());
        assertEquals(3, loaded.blockCount());
        assertEquals(schematic.bodies().get(1).corner(), loaded.bodies().get(1).corner());
        assertEquals(schematic.bodies().get(1).orientation(), loaded.bodies().get(1).orientation());
        assertEquals(2, loaded.preview(2).size());
        Quaterniond turn = loaded.bodies().get(1).orientation();
        turn.identity();
        assertNotEquals(turn, loaded.bodies().get(1).orientation());
    }

    // Reject duplicate block positions and limits even when a palette contains unavailable blocks
    @Test void malformedTemplatesCannotBypassLimits(){
        CompoundTag tag = SubLevelSchematicFiles.encode(template());
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 1, 16));
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 2));
        CompoundTag child = tag.getList("sub_levels", 10).getCompound(0);
        ListTag blocks = child.getList("blocks", 10);
        blocks.add(blocks.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16));
        blocks.removeLast();
        child.getList("palette", 10).getCompound(0).putString("Name", "missing:block");
        assertEquals(2, SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16).blockCount());
    }

    // Keep supported vanilla structure blocks at their original coordinates and charge only their items
    @Test void nativeStructuresSkipMissingBlocksAndTheirBlockEntities(){
        CompoundTag tag = SubLevelSchematicFiles.encode(template()).getList("sub_levels", Tag.TAG_COMPOUND).getCompound(0).copy();
        tag.getList("palette", Tag.TAG_COMPOUND).getCompound(0).putString("Name", "uninstalled:machine");
        tag.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).put("nbt", new CompoundTag());
        var schematic = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16);
        assertEquals(1, schematic.blockCount());
        assertEquals(new BlockPos(1, 1, 0), schematic.bodies().getFirst().blocks().getFirst().pos());
        var requirements = SchematicMaterials.requirements(mock(ServerLevel.class), schematic);
        assertEquals(1, requirements.size());
        assertEquals(Items.OAK_PLANKS, requirements.getFirst().stack().getItem());
        assertEquals(1, requirements.getFirst().count());
    }

    // Honor Photomancy's unavailable indexes and unknown registrations without shifting surviving blocks
    @Test void photomancySkipsUnavailablePaletteEntries(){
        for(boolean explicit : List.of(false, true)){
            CompoundTag tag = photomancy();
            CompoundTag body = tag.getList("sub_levels", Tag.TAG_COMPOUND).getCompound(0);
            if(explicit) body.putIntArray("unavailable_palette_ids", new int[]{0});
            else body.getList("block_palette", Tag.TAG_COMPOUND).getCompound(0).putString("Name", "uninstalled:machine");
            var schematic = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16);
            assertEquals(1, schematic.blockCount());
            assertEquals(new BlockPos(1, 1, 1), schematic.bodies().getFirst().blocks().getFirst().pos());
            assertEquals(new Vec3(4.5, 1, 5), rounded(schematic.bodies().getFirst().corner()));
            assertEquals("Kept configuration", schematic.bodies().getFirst().blocks().getFirst().data().getString("CustomName"));
        }
    }

    // Recompact Toolgun plots without moving supported blocks and drop welds to bodies with no available blocks
    @Test void toolgunSkipsMissingBlocksAndEmptyWeldEndpoints(){
        for(String format : List.of("enxv_aeronautics_plot_print_v8", "enxv_aeronautics_plot_print_v9")){
            CompoundTag tag = toolgun(); tag.putString("format", format);
            var before = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16);
            var expected = SubLevelSchematicBuild.placements(before, Vec3.ZERO, new Quaterniond()).stream()
                    .filter(row -> row.block().state().is(Blocks.CHEST)).findFirst().orElseThrow().worldPos();
            ListTag saved = tag.getList("sublevels", Tag.TAG_COMPOUND);
            CompoundTag src = saved.getCompound(0);
            ListTag palette = toolgunPalette(src);
            palette.getCompound(1).putString("Name", "uninstalled:machine");
            var partial = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16);
            assertEquals(1, partial.blockCount());
            assertEquals(rounded(expected), rounded(SubLevelSchematicBuild.placements(partial, Vec3.ZERO, new Quaterniond()).getFirst().worldPos()));
            assertEquals(new BlockPos(36, 131, -10), partial.bodies().getFirst().importFrame().origin());
            CompoundTag next = src.copy(); UUID nextId = UUID.randomUUID(); next.putUUID("sublevel_id", nextId); saved.add(next);
            palette.getCompound(2).putString("Name", "uninstalled:chest");
            CompoundTag joint = new CompoundTag();
            joint.putString("constraint_space", "saved_plot_local_v1"); joint.putString("mode", "FIXED");
            joint.putUUID("first_sublevel", src.getUUID("sublevel_id")); joint.putUUID("second_sublevel", nextId);
            joint.put("first_local", SableNBTUtils.writeVector3d(new Vector3d(35, 130, -11)));
            joint.put("second_local", SableNBTUtils.writeVector3d(new Vector3d(35, 130, -11)));
            ListTag joints = new ListTag(); joints.add(joint); tag.put("toolgun_constraints", joints);
            var loaded = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16);
            assertEquals(nextId, loaded.bodies().getFirst().id());
            assertEquals(1, loaded.bodies().size()); assertTrue(loaded.joints().isEmpty());
            saved.removeLast();
            tag.remove("toolgun_constraints");
            assertTrue(assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16))
                    .getMessage().contains("no available blocks"));
        }
    }

    // Read the native packed palette shared by the Toolgun fixtures
    private static ListTag toolgunPalette(CompoundTag body){
        return body.getCompound("plot").getCompound("chunks").getCompound(Long.toString(new ChunkPos(2, -1).toLong()))
                .getCompound("sections").getCompound("2").getCompound("block_states").getList("palette", Tag.TAG_COMPOUND);
    }

    // Drop native attachment frames only when their known body has no supported blocks
    @Test void nativeEmptyBodiesLoseTheirWeldsButUnknownEndpointsStillFail(){
        CompoundTag tag = SubLevelSchematicFiles.encode(template());
        ListTag bodies = tag.getList("sub_levels", Tag.TAG_COMPOUND);
        CompoundTag empty = bodies.getCompound(0), supported = bodies.getCompound(1);
        ListTag palette = empty.getList("palette", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < palette.size(); idx++) palette.getCompound(idx).putString("Name", "uninstalled:machine_" + idx);
        var joint = new SubLevelSchematic.Joint(empty.getUUID("uuid"), supported.getUUID("uuid"), SubLevelSchematic.JointType.FIXED,
                Vec3.ZERO, Vec3.ZERO, new Quaterniond(), Vec3.ZERO, Vec3.ZERO);
        ListTag joints = new ListTag(); joints.add(SubLevelSchematicFiles.writeJoint(joint)); tag.put("gadgetsngizmos:joints", joints);
        var schematic = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16);
        assertEquals(1, schematic.bodies().size()); assertEquals(1, schematic.blockCount()); assertTrue(schematic.joints().isEmpty());
        assertEquals(supported.getUUID("uuid"), schematic.bodies().getFirst().id());
        joints.getCompound(0).putUUID("First", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16));
    }

    // Keep exports inside /schematics and retain prior files instead of overwriting them
    @Test void exportedNamesCannotEscapeTheDirectory(@TempDir Path temp) throws Exception{
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.isDedicatedServer()).thenReturn(true);
        when(server.getServerDirectory()).thenReturn(temp);
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.save(server, "../escape", template()));
        assertEquals("assembly.nbt", SubLevelSchematicFiles.save(server, "assembly", template()));
        assertEquals("assembly-2.nbt", SubLevelSchematicFiles.save(server, "assembly", template()));
        assertEquals(2, SubLevelSchematicFiles.catalogue(server).size());
        assertTrue(Files.isRegularFile(temp.resolve("schematics/assembly.nbt")));
    }

    // Find mixed-case extensions and nested files while separating client identities
    @Test void catalogueFindsNestedNbtAndToolgunFiles(@TempDir Path temp) throws Exception{
        Files.createDirectories(temp.resolve("ships"));
        Files.write(temp.resolve("ships/craft.EXCRAFT"), new byte[]{1});
        Files.write(temp.resolve("house.NBT"), new byte[]{1});
        Files.write(temp.resolve("preview.png"), new byte[]{1});
        var shared = SubLevelSchematicFiles.catalogue(temp, "");
        var local = SubLevelSchematicFiles.catalogue(temp, "client:schematics/");
        assertEquals(2, shared.size());
        assertTrue(shared.stream().anyMatch(row -> row.name().equals("ships/craft.EXCRAFT")));
        assertNotEquals(shared.getFirst().id(), local.getFirst().id());
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.read(temp, "", local.getFirst().id()));
    }

    // Resolve selected files beyond the old cutoff even when new filenames change their order
    @Test void largeCataloguesKeepSelectionsReadableDuringFolderChanges(@TempDir Path temp) throws Exception{
        for(int idx = 0; idx < 512; idx++) Files.write(temp.resolve("ship-%04d.nbt".formatted(idx)), new byte[]{1, 2, 3});
        var entries = SubLevelSchematicFiles.catalogue(temp, "client:schematics/");
        assertEquals(512, entries.size());
        var selected = entries.get(127);
        Files.write(temp.resolve("aaa-new.excraft"), new byte[]{4});
        assertArrayEquals(new byte[]{1, 2, 3}, SubLevelSchematicFiles.read(temp, "client:schematics/", selected.id()));
        assertArrayEquals(new byte[]{1, 2, 3}, SubLevelSchematicFiles.read(temp, "client:schematics/", entries.getLast()));
        assertArrayEquals(new byte[]{1, 2, 3}, SubLevelSchematicFiles.read(temp, "client:schematics/", selected));
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.read(temp, "", selected));
        var escaped = new SubLevelSchematicFiles.Entry(UUID.nameUUIDFromBytes("../outside.nbt".getBytes(
                java.nio.charset.StandardCharsets.UTF_8)), "../outside.nbt");
        assertThrows(java.io.IOException.class, () -> SubLevelSchematicFiles.read(temp, "", escaped));
    }

    // Keep each spatial section independent and every vertical column assigned to one builder
    @Test void constructionSectionsKeepColumnsTogetherAndBuildUpwards(){
        var blocks = new java.util.ArrayList<SubLevelSchematic.Block>();
        for(int y = 0; y < 6; y++) for(int z = 0; z < 8; z++) for(int x = 0; x < 16; x++)
            blocks.add(new SubLevelSchematic.Block(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), new CompoundTag()));
        var schematic = new SubLevelSchematic(List.of(new SubLevelSchematic.Body(UUID.randomUUID(), Vec3.ZERO,
                new Quaterniond(), new BlockPos(16, 6, 8), blocks)));
        var placements = SubLevelSchematicBuild.placements(schematic, new Vec3(20, 60, 30), new Quaterniond());
        var sections = SubLevelSchematicBuild.sections(placements, 4);
        assertEquals(4, sections.size());
        var columns = new java.util.HashMap<String, Integer>();
        var seen = new java.util.HashSet<SubLevelSchematicBuild.Placement>();
        for(int idx = 0; idx < sections.size(); idx++){
            int height = Integer.MIN_VALUE;
            assertEquals(192, sections.get(idx).size());
            for(var row : sections.get(idx)){
                assertTrue(seen.add(row));
                assertTrue(row.worldPos().y >= height); height = (int) row.worldPos().y;
                String column = row.block().pos().getX() + ":" + row.block().pos().getZ();
                Integer prev = columns.putIfAbsent(column, idx);
                if(prev != null) assertEquals(idx, prev);
            }
        }
        assertEquals(placements.size(), seen.size());
        assertEquals(sections, SubLevelSchematicBuild.sections(placements, 4));
        assertEquals(List.of(placements), SubLevelSchematicBuild.sections(placements, 1));
        assertEquals(List.of(), SubLevelSchematicBuild.sections(List.of(), 4));
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicBuild.sections(placements, 0));
    }

    // Keep a LAN host's private game folder outside the shared world catalogue
    @Test void integratedSharedFilesUseTheWorldDirectory(@TempDir Path temp) throws Exception{
        Files.createDirectories(temp.resolve("schematics"));
        Files.write(temp.resolve("schematics/private.nbt"), new byte[]{1});
        Files.createDirectories(temp.resolve("world/schematics"));
        Files.write(temp.resolve("world/schematics/shared.nbt"), new byte[]{1});
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getServerDirectory()).thenReturn(temp);
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(temp.resolve("world"));
        var shared = SubLevelSchematicFiles.catalogue(server);
        assertEquals(List.of("shared.nbt"), shared.stream().map(SubLevelSchematicFiles.Entry::name).toList());
    }

    // Accept raw and gzip NBT without relying on a filename extension
    @Test void rawAndCompressedStructuresDecodeTheSameBlocks() throws Exception{
        CompoundTag tag = SubLevelSchematicFiles.encode(template());
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(raw));
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, compressed);
        assertEquals(3, SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), raw.toByteArray(), 8, 16).blockCount());
        assertEquals(3, SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), compressed.toByteArray(), 8, 16).blockCount());
        assertThrows(java.io.IOException.class, () -> SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), new byte[]{1, 2}, 8, 16));
    }

    // Preserve Photomancy palette references, configuration and an asymmetric rotated anchor
    @Test void photomancyBlueprintsKeepGeometryAndBlockData(){
        CompoundTag tag = photomancy();
        var schematic = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16);
        assertEquals(2, schematic.blockCount());
        var body = schematic.bodies().getFirst();
        assertEquals(new Vec3(4.5, 1, 5), rounded(body.corner()));
        assertEquals(Blocks.CHEST.defaultBlockState(), body.blocks().get(1).state());
        assertEquals("Kept configuration", body.blocks().get(1).data().getString("CustomName"));
        CompoundTag src = tag.getList("sub_levels", Tag.TAG_COMPOUND).getCompound(0);
        src.getList("blocks", Tag.TAG_COMPOUND).getCompound(1).putInt("block_entity_data_id", 4);
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16));
    }

    // Read Toolgun's packed plot data and its explicit source height and local anchor
    @Test void toolgunBlueprintsDecodeV8AndV9Plots(){
        CompoundTag tag = toolgun();
        for(String format : List.of("enxv_aeronautics_plot_print_v8", "enxv_aeronautics_plot_print_v9")){
            tag.putString("format", format);
            var schematic = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16);
            assertEquals(2, schematic.blockCount());
            var body = schematic.bodies().getFirst();
            assertEquals(new Vec3(1, -4, -1), rounded(body.corner()));
            assertEquals(new BlockPos(2, 2, 2), body.size());
            assertEquals(SubLevelSchematic.Format.TOOLGUN, body.importFrame().format());
            assertEquals(new BlockPos(35, 130, -11), body.importFrame().origin());
            assertEquals(Blocks.CHEST.defaultBlockState(), body.blocks().get(1).state());
            assertEquals("minecraft:chest", body.blocks().get(1).data().getString("id"));
        }
        tag.getList("sublevels", Tag.TAG_COMPOUND).getCompound(0).getCompound("plot").getCompound("chunks")
                .getCompound(Long.toString(new ChunkPos(2, -1).toLong())).getCompound("sections").getCompound("2")
                .getCompound("block_states").putLongArray("data", new long[]{0});
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16));
    }

    // Reject dynamic entity blueprints visibly rather than building an incomplete vehicle
    @Test void foreignEntityPayloadsAreRejected(){
        CompoundTag tag = photomancy();
        ListTag entities = new ListTag(); entities.add(new CompoundTag());
        tag.getList("sub_levels", Tag.TAG_COMPOUND).getCompound(0).put("entities", entities);
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16));
    }

    // Preserve Toolgun weld modes and frames when compacting and exporting their plots
    @Test void toolgunWeldsKeepCompactedFramesAndSurviveNativeRoundTrip(){
        CompoundTag tag = toolgun();
        ListTag saved = tag.getList("sublevels", Tag.TAG_COMPOUND);
        CompoundTag next = saved.getCompound(0).copy(); next.putUUID("sublevel_id", UUID.randomUUID()); saved.add(next);
        ListTag constraints = new ListTag();
        for(var type : SubLevelSchematic.JointType.values()){
            CompoundTag joint = new CompoundTag();
            joint.putString("constraint_space", "saved_plot_local_v1"); joint.putString("mode", type.name());
            joint.putUUID("first_sublevel", saved.getCompound(0).getUUID("sublevel_id"));
            joint.putUUID("second_sublevel", next.getUUID("sublevel_id"));
            joint.put("first_local", SableNBTUtils.writeVector3d(new Vector3d(35.5, 130.5, -10.5)));
            joint.put("second_local", SableNBTUtils.writeVector3d(new Vector3d(36.5, 131.5, -9.5)));
            joint.put("relative_orientation", SableNBTUtils.writeQuaternion(new Quaterniond().rotateY(0.3)));
            joint.put("first_axis_local", SableNBTUtils.writeVector3d(new Vector3d(0, 2, 0)));
            joint.put("second_axis_local", SableNBTUtils.writeVector3d(new Vector3d(0, 0, 2)));
            constraints.add(joint);
        }
        tag.put("toolgun_constraints", constraints);
        var schematic = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16);
        assertEquals(3, schematic.joints().size());
        assertEquals(new Vec3(0.5, 0.5, 0.5), schematic.joints().getFirst().firstAnchor());
        assertEquals(new Vec3(1.5, 1.5, 1.5), schematic.joints().getFirst().secondAnchor());
        assertEquals(new Vec3(0, 1, 0), schematic.joints().get(2).firstAxis());
        assertEquals(new Vec3(0, 0, 1), schematic.joints().get(2).secondAxis());
        var restored = SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), SubLevelSchematicFiles.encode(schematic), 8, 16);
        assertEquals(schematic.joints(), restored.joints());
        ServerLevel level = mock(ServerLevel.class); when(level.registryAccess()).thenReturn(registries);
        try(var loader = mockStatic(LoadingModList.class)){
            loader.when(LoadingModList::get).thenReturn(mock(LoadingModList.class));
            assertEquals(schematic.joints(), SubLevelSchematicFiles.sanitize(level, schematic).joints());
        }
        constraints.getCompound(0).putUUID("second_sublevel", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> SubLevelSchematicFiles.decode(BuiltInRegistries.BLOCK.asLookup(), tag, 8, 16));
    }

    // Reject unowned metadata and malformed attachment axes before touching native physics
    @Test void retainedWeldsRequireTheirOwnerAndValidFrames(){
        var template = template();
        UUID first = template.bodies().getFirst().id(), second = template.bodies().get(1).id();
        var joint = new SubLevelSchematic.Joint(first, second, SubLevelSchematic.JointType.FIXED,
                Vec3.ZERO, new Vec3(1, 2, 3), new Quaterniond(), null, null);
        ListTag rows = new ListTag(); rows.add(SubLevelSchematicFiles.writeJoint(joint));
        CompoundTag data = new CompoundTag(); data.put(SubLevelSchematicJoints.KEY, rows);
        assertEquals(List.of(joint), SubLevelSchematicJoints.retained(data, first));
        assertTrue(SubLevelSchematicJoints.retained(data, second).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new SubLevelSchematic.Joint(first, first,
                SubLevelSchematic.JointType.FIXED, Vec3.ZERO, Vec3.ZERO, new Quaterniond(), null, null));
        assertThrows(IllegalArgumentException.class, () -> new SubLevelSchematic.Joint(first, second,
                SubLevelSchematic.JointType.BEARING, Vec3.ZERO, Vec3.ZERO, new Quaterniond(), Vec3.ZERO, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new SubLevelSchematic(template.bodies(), List.of(
                new SubLevelSchematic.Joint(first, UUID.randomUUID(), SubLevelSchematic.JointType.FIXED,
                        Vec3.ZERO, Vec3.ZERO, new Quaterniond(), null, null))));
    }

    // Verify native attachment ownership and recreation without duplicate live constraints
    @Test void completedWeldsUseFreshPlotCoordinatesAndRestoreAfterWorldLoad() throws Exception{
        var template = template();
        var joint = new SubLevelSchematic.Joint(template.bodies().getFirst().id(), template.bodies().get(1).id(),
                SubLevelSchematic.JointType.FIXED, new Vec3(0.5, 1, 0.5), new Vec3(0.5, 0.5, 0.5), new Quaterniond(), null, null);
        var schematic = new SubLevelSchematic(template.bodies(), List.of(joint));
        ServerLevel level = mock(ServerLevel.class);
        ServerSubLevelContainer container = mock(ServerSubLevelContainer.class);
        SubLevelPhysicsSystem system = mock(SubLevelPhysicsSystem.class);
        PhysicsPipeline pipeline = mock(PhysicsPipeline.class);
        when(container.physicsSystem()).thenReturn(system); when(system.getPipeline()).thenReturn(pipeline);
        ServerSubLevel first = schematicBody(new BlockPos(1024, 0, 2048)), second = schematicBody(new BlockPos(3072, 0, 4096));
        when(container.getSubLevel(first.getUniqueId())).thenReturn(first); when(container.getSubLevel(second.getUniqueId())).thenReturn(second);
        doReturn(List.of(first, second)).when(container).getAllSubLevels();
        PhysicsConstraintHandle handle = mock(FixedConstraintHandle.class); when(handle.isValid()).thenReturn(true);
        doReturn(handle).when(pipeline).addConstraint(any(), any(), any());
        try(var lookup = mockStatic(SubLevelContainer.class)){
            lookup.when(() -> SubLevelContainer.getContainer(level)).thenReturn(container);
            SubLevelSchematicJoints.install(level, schematic, List.of(first, second));
            var retained = SubLevelSchematicJoints.retained(first);
            assertEquals(1, retained.size()); assertEquals(first.getUniqueId(), retained.getFirst().first());
            assertEquals(second.getUniqueId(), retained.getFirst().second());
            assertEquals(new Vec3(1024.5, 1, 2048.5), retained.getFirst().firstAnchor());
            assertEquals(new Vec3(3072.5, 0.5, 4096.5), retained.getFirst().secondAnchor());
            var tick = mock(LevelTickEvent.Post.class); when(tick.getLevel()).thenReturn(level);
            SubLevelSchematicJoints.onLevelTick(tick);
            verify(pipeline, times(1)).addConstraint(any(), any(), any());
            var unload = mock(LevelEvent.Unload.class); when(unload.getLevel()).thenReturn(level);
            SubLevelSchematicJoints.onLevelUnload(unload);
            SubLevelSchematicJoints.onLevelTick(tick);
            SubLevelSchematicJoints.onLevelTick(tick);
            verify(pipeline, times(2)).addConstraint(any(), any(), any());
            SubLevelSchematicJoints.onLevelUnload(unload);
        }
    }

    // Give a fresh native body mutable saved metadata without using a running physics scene
    private static ServerSubLevel schematicBody(BlockPos center){
        ServerSubLevel body = mock(ServerSubLevel.class); when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        ServerLevelPlot plot = mock(ServerLevelPlot.class); when(body.getPlot()).thenReturn(plot); when(plot.getCenterBlock()).thenReturn(center);
        CompoundTag[] data = {new CompoundTag()};
        when(body.getUserDataTag()).thenAnswer(ctx -> data[0]);
        doAnswer(ctx -> { data[0] = ctx.getArgument(0); return null; }).when(body).setUserDataTag(any());
        return body;
    }

    // Isolate ordered client uploads by player and release them on disconnect
    @Test void privateUploadsRequireTheirOwnerAndCompleteOrderedChunks(){
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID(), id = UUID.randomUUID();
        assertFalse(SchematicClientFiles.receive(server, owner, id, 4, 0, new byte[]{1, 2}));
        assertFalse(SchematicClientFiles.contains(server, owner, id));
        assertThrows(IllegalArgumentException.class, () -> SchematicClientFiles.receive(server, owner, id, 4, 3, new byte[]{3}));
        assertTrue(SchematicClientFiles.receive(server, owner, id, 4, 2, new byte[]{3, 4}));
        assertTrue(SchematicClientFiles.contains(server, owner, id));
        assertFalse(SchematicClientFiles.contains(server, other, id));
        assertThrows(IllegalArgumentException.class, () -> SchematicClientFiles.receive(server, other, id,
                SchematicClientFiles.MAXIMUM_BYTES + 1, 0, new byte[]{1}));
        SchematicClientFiles.remove(server, owner);
        assertFalse(SchematicClientFiles.contains(server, owner, id));
    }

    // Reopening a selection must not make its ready bytes unavailable during another upload
    @Test void readyUploadsSurviveIncompleteReplacementsAndCancellation(){
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID(), next = UUID.randomUUID();
        assertTrue(SchematicClientFiles.receive(server, owner, id, 2, 0, new byte[]{1, 2}));
        assertFalse(SchematicClientFiles.receive(server, owner, id, 4, 0, new byte[]{1, 2}));
        assertTrue(SchematicClientFiles.contains(server, owner, id));
        SchematicClientFiles.cancelUpload(server, owner);
        assertTrue(SchematicClientFiles.contains(server, owner, id));
        assertFalse(SchematicClientFiles.receive(server, owner, next, 4, 0, new byte[]{3, 4}));
        assertTrue(SchematicClientFiles.contains(server, owner, id));
        assertFalse(SchematicClientFiles.contains(server, owner, next));
        assertTrue(SchematicClientFiles.receive(server, owner, next, 4, 2, new byte[]{5, 6}));
        assertFalse(SchematicClientFiles.contains(server, owner, id));
        assertTrue(SchematicClientFiles.contains(server, owner, next));
        SchematicClientFiles.remove(server, owner);
    }

    // Filter missing inventory items before the real chest loader parses their registry ids
    @Test void missingInventoryItemsDoNotDiscardAvailableBlocksOrItems(){
        ServerLevel level = mock(ServerLevel.class); when(level.registryAccess()).thenReturn(registries);
        CompoundTag data = new CompoundTag(); ListTag items = new ListTag(); data.put("Items", items);
        CompoundTag missing = new CompoundTag(); missing.putString("id", "absent_mod:machine");
        missing.putInt("count", 16); missing.putByte("Slot", (byte) 0); items.add(missing);
        CompoundTag available = new CompoundTag(); available.putString("id", "minecraft:stone");
        available.putInt("count", 3); available.putByte("Slot", (byte) 1); items.add(available);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) SchematicBlockData.create(
                level, BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), data);
        assertTrue(chest.getItem(0).isEmpty());
        assertEquals(Items.STONE, chest.getItem(1).getItem());
        assertEquals(3, chest.getItem(1).getCount());
        assertEquals(2, data.getList("Items", Tag.TAG_COMPOUND).size());
    }

    // A train door's upper half remains a schematic block without a block entity
    @Test void trainDoorUpperHalvesCanBeSanitized(){
        var door = mock(com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock.class);
        when(door.newBlockEntity(any(), any())).thenCallRealMethod();
        var state = spy(Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        doReturn(door).when(state).getBlock();
        var block = new SubLevelSchematic.Block(BlockPos.ZERO, state, new CompoundTag());
        var body = new SubLevelSchematic.Body(UUID.randomUUID(), Vec3.ZERO, new Quaterniond(),
                new BlockPos(1, 1, 1), List.of(block));
        ServerLevel level = mock(ServerLevel.class); when(level.registryAccess()).thenReturn(registries);
        var safe = SubLevelSchematicFiles.sanitize(level, new SubLevelSchematic(List.of(body)));
        assertEquals(1, safe.blockCount());
        assertEquals(state, safe.bodies().getFirst().blocks().getFirst().state());
    }

    // Clean decoded controller payloads without discarding surrounding graph configuration
    @Test void unavailableNestedFrequencyItemsAndComponentsAreRemoved(){
        CompoundTag graph = new CompoundTag(); graph.putString("Name", "controller graph");
        CompoundTag missing = new CompoundTag(); missing.putString("id", "absent_mod:motor"); missing.putInt("count", 1);
        graph.put("Frequency", missing);
        CompoundTag available = new CompoundTag(); available.putString("id", "minecraft:stone"); available.putInt("count", 1);
        CompoundTag components = new CompoundTag(); components.putString("absent_mod:variant", "worker");
        components.putString("minecraft:custom_name", "\"Filter\""); available.put("components", components);
        graph.put("Filter", available);
        var res = com.rieno.gadgetsandgizmos.lib.inventory.ItemStackNbtSanitizer.withoutUnavailableItems(graph);
        assertFalse(res.contains("Frequency"));
        assertEquals("controller graph", res.getString("Name"));
        assertFalse(res.getCompound("Filter").getCompound("components").contains("absent_mod:variant"));
        assertTrue(res.getCompound("Filter").getCompound("components").contains("minecraft:custom_name"));
        assertTrue(graph.contains("Frequency"));
    }

    // Run private uploaded bytes through the server registry and the normal safe conversion
    @Test void uploadedStructuresUseTheSameServerDecoder() throws Exception{
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        when(server.registryAccess()).thenReturn(registries.freeze());
        ServerLevel level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(server); when(level.registryAccess()).thenReturn(registries);
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(SubLevelSchematicFiles.encode(template()), bytes);
        byte[] data = bytes.toByteArray();
        assertTrue(SchematicClientFiles.receive(server, owner, id, data.length, 0, data));
        var loaded = SchematicClientFiles.load(level, owner, id, 8, 16);
        assertEquals(3, loaded.blockCount());
        assertTrue(loaded.bodies().stream().allMatch(body -> body.importFrame().format() == SubLevelSchematic.Format.NATIVE));
        assertThrows(IllegalArgumentException.class, () -> SchematicClientFiles.load(level, UUID.randomUUID(), id, 8, 16));
        SchematicClientFiles.remove(server, owner);
    }

    // Build bottom-to-top across connected bodies after free world rotation
    @Test void rotatedAssemblyLayersRemainOrdered(){
        var plan = SubLevelSchematicBuild.placements(template(), new Vec3(10, 20, 30), new Quaterniond().rotateZ(Math.PI / 2));
        assertEquals(3, plan.size());
        for(int idx = 1; idx < plan.size(); idx++) assertTrue(Math.floor(plan.get(idx - 1).worldPos().y) <= Math.floor(plan.get(idx).worldPos().y));
        assertTrue(plan.stream().anyMatch(row -> row.bodyIdx() == 1));
    }

    // Respect strict item components and avoid counting one stack for two requirements
    @Test void missingMaterialsIncludeSharedAndStrictStock(){
        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Exact stone"));
        ItemStackHandler inventory = new ItemStackHandler(2);
        inventory.setStackInSlot(0, named.copyWithCount(2));
        inventory.setStackInSlot(1, new ItemStack(Items.STONE, 3));
        var endpoint = endpoint(inventory);
        var requirements = List.of(new SchematicMaterials.Requirement(named, 2, true, false),
                new SchematicMaterials.Requirement(new ItemStack(Items.STONE), 4, false, false));
        var missing = SchematicMaterials.shortages(requirements, List.of(endpoint));
        assertEquals(0, missing.getFirst().missing());
        assertEquals(1, missing.get(1).missing());
        assertEquals(2, inventory.getStackInSlot(0).getCount());
        assertEquals(3, inventory.getStackInSlot(1).getCount());
    }

    // Reserve complete stock and refund its exact components at most once
    @Test void reservationWithdrawsAndRefundsRealItems(){
        ItemStackHandler inventory = new ItemStackHandler(1);
        ItemStack named = new ItemStack(Items.STONE, 8);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Stored stone"));
        inventory.setStackInSlot(0, named);
        ServerPlayer player = player();
        var reservation = SchematicMaterials.reserve(player, Vec3.ZERO,
                List.of(new SchematicMaterials.Requirement(named, 5, true, false)), List.of(endpoint(inventory)));
        assertEquals(3, inventory.getStackInSlot(0).getCount());
        reservation.refund(); reservation.refund();
        assertEquals(8, inventory.getStackInSlot(0).getCount());
        assertEquals(Component.literal("Stored stone"), inventory.getStackInSlot(0).get(DataComponents.CUSTOM_NAME));
        var complete = SchematicMaterials.reserve(player, Vec3.ZERO,
                List.of(new SchematicMaterials.Requirement(named, 5, true, false)), List.of(endpoint(inventory)));
        complete.commit(); complete.refund();
        assertEquals(3, inventory.getStackInSlot(0).getCount());
    }

    // Return partially withdrawn stock if an endpoint changes after the availability check
    @Test void changedStockRollsBackTheWholeReservation(){
        ItemStackHandler inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(Items.STONE, 2));
        var endpoint = spy(endpoint(inventory));
        doReturn(5L).when(endpoint).availableMatchingItem(any(), any());
        var requirement = new SchematicMaterials.Requirement(new ItemStack(Items.STONE), 5, false, false);
        assertThrows(IllegalArgumentException.class, () -> SchematicMaterials.reserve(player(), Vec3.ZERO,
                List.of(requirement), List.of(endpoint)));
        assertEquals(2, inventory.getStackInSlot(0).getCount());
    }

    // Hold a sufficiently durable tool and return it with wear only after completion
    @Test void reusableToolsReturnWithConstructionWear(){
        ItemStackHandler inventory = new ItemStackHandler(1);
        ItemStack tool = new ItemStack(Items.IRON_AXE);
        tool.setDamageValue(10);
        inventory.setStackInSlot(0, tool);
        var requirement = new SchematicMaterials.Requirement(new ItemStack(Items.IRON_AXE), 20, false, true);
        var reservation = SchematicMaterials.reserve(player(), Vec3.ZERO, List.of(requirement), List.of(endpoint(inventory)));
        assertTrue(inventory.getStackInSlot(0).isEmpty());
        reservation.commit(); reservation.commit();
        assertEquals(30, inventory.getStackInSlot(0).getDamageValue());
        assertFalse(new SchematicMaterials.Requirement(new ItemStack(Items.IRON_AXE), 250, false, true)
                .matches(inventory.getStackInSlot(0)));
    }

    // Require both slabs for a double slab using Create's requirement implementation
    @Test void specialBlockCountsUseCreateRequirements(){
        ServerLevel level = mock(ServerLevel.class);
        var state = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE);
        var schematic = new SubLevelSchematic(List.of(new SubLevelSchematic.Body(UUID.randomUUID(), Vec3.ZERO, new Quaterniond(),
                new BlockPos(1, 1, 1), List.of(new SubLevelSchematic.Block(BlockPos.ZERO, state, null)))));
        var requirements = SchematicMaterials.requirements(level, schematic);
        assertEquals(2, requirements.getFirst().amount());
        assertEquals(Items.OAK_SLAB, requirements.getFirst().stack().getItem());
    }

    // Create a real component-aware inventory endpoint with insertion enabled
    private static WorkerInventoryEndpoint endpoint(ItemStackHandler inventory){
        return new WorkerInventoryEndpoint(UUID.randomUUID(), () -> BlockPos.ZERO, inventory, registries, stack -> true, true);
    }

    // Provide the owning server thread and registry context for material custody
    private static ServerPlayer player(){
        ServerPlayer player = mock(ServerPlayer.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(player.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        ServerLevel level = mock(ServerLevel.class);
        when(level.registryAccess()).thenReturn(registries);
        when(player.serverLevel()).thenReturn(level);
        return player;
    }

    // Create two connected bodies with different positions and orientations
    private static SubLevelSchematic template(){
        return new SubLevelSchematic(List.of(
                new SubLevelSchematic.Body(UUID.randomUUID(), Vec3.ZERO, new Quaterniond(), new BlockPos(2, 2, 2), List.of(
                        new SubLevelSchematic.Block(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null),
                        new SubLevelSchematic.Block(new BlockPos(1, 1, 0), Blocks.OAK_PLANKS.defaultBlockState(), null))),
                new SubLevelSchematic.Body(UUID.randomUUID(), new Vec3(3, 1, 0), new Quaterniond().rotateX(Math.PI / 2), new BlockPos(1, 1, 1), List.of(
                        new SubLevelSchematic.Block(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)))));
    }

    // Remove floating-point noise from independently calculated rotated corners
    private static Vec3 rounded(Vec3 pos){
        return new Vec3(Math.rint(pos.x * 1E8) / 1E8, Math.rint(pos.y * 1E8) / 1E8, Math.rint(pos.z * 1E8) / 1E8);
    }

    // Create a Photomancy v1 fixture using its documented palette and block entity indexes
    private static CompoundTag photomancy(){
        CompoundTag root = new CompoundTag(); root.putInt("version", 1);
        CompoundTag body = new CompoundTag(); body.putUUID("source_uuid", UUID.randomUUID());
        CompoundTag bounds = new CompoundTag();
        for(String axis : List.of("x", "y", "z")){ bounds.putInt("min_" + axis, 100); bounds.putInt("max_" + axis, 101); }
        body.put("local_bounds", bounds);
        CompoundTag anchor = new CompoundTag(); anchor.putDouble("minX", -1); anchor.putDouble("minY", -0.5); anchor.putDouble("minZ", -1);
        body.put("anchor_bounds", anchor);
        CompoundTag pose = new CompoundTag(); pose.put("position", SableNBTUtils.writeVector3d(new Vector3d(4, 2, 6)));
        pose.put("orientation", SableNBTUtils.writeQuaternion(new Quaterniond().rotateZ(Math.PI / 2))); body.put("relative_pose", pose);
        ListTag palette = new ListTag(); palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
        palette.add(NbtUtils.writeBlockState(Blocks.CHEST.defaultBlockState())); body.put("block_palette", palette);
        ListTag blocks = new ListTag();
        for(int idx = 0; idx < 2; idx++){
            CompoundTag block = new CompoundTag(); CompoundTag pos = new CompoundTag();
            pos.putInt("x", idx); pos.putInt("y", idx); pos.putInt("z", idx);
            block.put("local_pos", pos); block.putInt("palette_id", idx);
            if(idx == 1) block.putInt("block_entity_data_id", 0);
            blocks.add(block);
        }
        body.put("blocks", blocks);
        CompoundTag chest = new CompoundTag(); chest.putString("id", "minecraft:chest"); chest.putString("CustomName", "Kept configuration");
        ListTag entities = new ListTag(); entities.add(chest); body.put("block_entities", entities);
        ListTag bodies = new ListTag(); bodies.add(body); root.put("sub_levels", bodies);
        return root;
    }

    // Create a Toolgun native plot fixture with two independently packed block states
    private static CompoundTag toolgun(){
        UUID id = UUID.randomUUID();
        CompoundTag root = new CompoundTag(); root.putString("format", "enxv_aeronautics_plot_print_v8");
        root.putUUID("root_sublevel", id); root.putInt("source_min_build_height", 96);
        CompoundTag body = new CompoundTag(); body.putUUID("sublevel_id", id);
        body.put("relative_position", SableNBTUtils.writeVector3d(new Vector3d(3, 1, 2)));
        body.put("relative_orientation", SableNBTUtils.writeQuaternion(new Quaterniond().rotateZ(Math.PI / 2)));
        body.put("local_anchor", SableNBTUtils.writeVector3d(new Vector3d(40, 128, -8)));
        body.putString("local_anchor_space", "saved_plot_local_v1");
        ListTag palette = new ListTag(); palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState())); palette.add(NbtUtils.writeBlockState(Blocks.CHEST.defaultBlockState()));
        long[] values = new long[256];
        int stone = (2 << 8) | (5 << 4) | 3, chest = (3 << 8) | (6 << 4) | 4;
        values[stone / 16] |= 1L << ((stone % 16) * 4); values[chest / 16] |= 2L << ((chest % 16) * 4);
        CompoundTag states = new CompoundTag(); states.put("palette", palette); states.putLongArray("data", values);
        CompoundTag section = new CompoundTag(); section.put("block_states", states);
        CompoundTag sections = new CompoundTag(); sections.put("2", section);
        CompoundTag chunk = new CompoundTag(); chunk.put("sections", sections);
        CompoundTag data = new CompoundTag(); data.putString("id", "minecraft:chest");
        data.putInt("x", 36); data.putInt("y", 131); data.putInt("z", -10);
        ListTag entities = new ListTag(); entities.add(data); chunk.put("block_entities", entities);
        CompoundTag chunks = new CompoundTag(); chunks.put(Long.toString(new ChunkPos(2, -1).toLong()), chunk);
        CompoundTag plot = new CompoundTag(); plot.put("chunks", chunks); body.put("plot", plot);
        ListTag bodies = new ListTag(); bodies.add(body); root.put("sublevels", bodies);
        return root;
    }
}
