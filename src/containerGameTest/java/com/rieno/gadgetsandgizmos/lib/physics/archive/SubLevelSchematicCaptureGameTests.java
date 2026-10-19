package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

// Save a live assembly repeatedly without removing its source blocks, connections or contents
@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class SubLevelSchematicCaptureGameTests{
    // Keep the framework entry point static
    private SubLevelSchematicCaptureGameTests(){}


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read live block entities and preserve the source body through two independent exports
    @GameTest(template = "empty_container")
    public static void savingCopiesTheWorldWithoutConsumingIt(GameTestHelper ctx){
        var level = ctx.getLevel();
        var container = SubLevelContainer.getContainer(level);
        Pose3d pose = new Pose3d();
        BlockPos worldPos = ctx.absolutePos(new BlockPos(1, 4, 1));
        pose.position().set(worldPos.getX(), worldPos.getY(), worldPos.getZ());
        var body = (ServerSubLevel)container.allocateNewSubLevel(pose);
        try{
            var plot = body.getPlot(); plot.newEmptyChunk(plot.getCenterChunk());
            var chunk = plot.getLoadedChunks().iterator().next().getChunk();
            BlockPos center = plot.getCenterBlock();
            chunk.setBlockState(center, Blocks.STONE.defaultBlockState(), false);
            var chain = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "chain_conveyor")).defaultBlockState();
            chunk.setBlockState(center.offset(1, 0, 0), chain, false); chunk.setBlockState(center.offset(3, 0, 0), chain, false);
            chunk.setBlockState(center.offset(0, 0, 1), Blocks.CHEST.defaultBlockState(), false);
            var track = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "track")).defaultBlockState();
            BlockPos trackPos = center.offset(4, 0, 0);
            chunk.setBlockState(trackPos, track, false);
            ctx.assertTrue(track.hasBlockEntity() && chunk.getBlockEntity(trackPos) == null,
                    "Plain track fixture must declare entity support without creating an entity");
            var first = (ChainConveyorBlockEntity)chunk.getBlockEntity(center.offset(1, 0, 0));
            var second = (ChainConveyorBlockEntity)chunk.getBlockEntity(center.offset(3, 0, 0));
            first.connections.add(new BlockPos(2, 0, 0)); second.connections.add(new BlockPos(-2, 0, 0));
            var chest = (ChestBlockEntity)chunk.getBlockEntity(center.offset(0, 0, 1));
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
            var before = chest.saveWithFullMetadata(level.registryAccess());
            container.physicsSystem().getPipeline().onStatsChanged(body);
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            player.setPos(worldPos.getCenter());
            var firstCopy = SubLevelSchematicCapture.capture(player, body.getUniqueId(), 32, 8, 64);
            var secondCopy = SubLevelSchematicCapture.capture(player, body.getUniqueId(), 32, 8, 64);
            boolean refused = false;
            try{ SubLevelSchematicCapture.capture(player, body.getUniqueId(), 32, 8, 3); }
            catch(IllegalArgumentException err){ refused = true; }
            ctx.assertTrue(refused, "Capture ignored the configured block limit");
            ctx.assertTrue(firstCopy.blockCount() == 5 && secondCopy.blockCount() == 5, "World capture omitted source blocks");
            var copiedTrack = firstCopy.bodies().getFirst().blocks().stream()
                    .filter(val -> val.pos().equals(new BlockPos(4, 0, 0))).findFirst().orElseThrow();
            ctx.assertTrue(copiedTrack.state().equals(track) && copiedTrack.data().isEmpty(),
                    "Capture changed a track state without an entity");
            ctx.assertTrue(chunk.getBlockState(trackPos).equals(track) && chunk.getBlockEntity(trackPos) == null,
                    "Capture changed the source track or created an unnecessary entity");
            ctx.assertTrue(!body.isRemoved() && container.getSubLevel(body.getUniqueId()) == body, "Saving consumed the live sublevel");
            ctx.assertTrue(chunk.getBlockEntity(chest.getBlockPos()) == chest, "Saving replaced the source block entity");
            ctx.assertTrue(before.equals(chest.saveWithFullMetadata(level.registryAccess())), "Saving changed source contents");
            ctx.assertTrue(first.connections.contains(new BlockPos(2, 0, 0)), "Saving changed source chain links");
            ctx.assertTrue(!firstCopy.bodies().getFirst().id().equals(secondCopy.bodies().getFirst().id()), "Exports share template identities");
            var copiedChain = firstCopy.bodies().getFirst().blocks().stream().filter(val -> val.pos().equals(new BlockPos(1, 0, 0))).findFirst().orElseThrow();
            ctx.assertTrue(copiedChain.data().getCompound(SchematicConnectionData.TAG_KEY).getList("Endpoints", 10).size() == 1,
                    "World capture discarded the internal chain connection");
            ctx.assertTrue(dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext.getCurrentContext() == null,
                    "Saving left a schematic context active");
            ctx.succeed();
        }finally{
            if(container.getSubLevel(body.getUniqueId()) == body) SableSubLevelLifecycleApi.remove(level, List.of(body));
        }
    }
}
