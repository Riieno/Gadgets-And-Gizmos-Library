package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/


import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

// Exercise safe export and delayed connection restoration against real Create block entities
@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class SchematicConnectionGameTests{
    // Keep test setup on the framework's static entry points
    private SchematicConnectionGameTests(){}


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Reconnect both chain ends after safe placement recreated their block entities
    @GameTest(template = "empty_container")
    public static void chainsReconnectAfterSafePlacement(GameTestHelper ctx){
        BlockPos first = new BlockPos(1, 1, 1), second = new BlockPos(3, 1, 1);
        ctx.setBlock(first, state("chain_conveyor"));
        ctx.setBlock(second, state("chain_conveyor"));
        var from = (ChainConveyorBlockEntity)ctx.getBlockEntity(first);
        var to = (ChainConveyorBlockEntity)ctx.getBlockEntity(second);
        from.connections.add(second.subtract(first)); to.connections.add(first.subtract(second));
        BlockPos destination = ctx.absolutePos(new BlockPos(1, 1, 3));
        UUID source = UUID.randomUUID(), template = UUID.randomUUID();
        var frames = List.of(new SchematicConnectionData.BodyFrame(source, template, from.getBlockPos(), new BlockPos(3, 1, 1)));
        CompoundTag firstData = capture(ctx, from, source, frames), secondData = capture(ctx, to, source, frames);
        var mapping = mapping(template, destination);
        BlockPos endpoint = destination.offset(2, 0, 0);
        BlockHelper.placeSchematicBlock(ctx.getLevel(), from.getBlockState(), destination, ItemStack.EMPTY, firstData);
        BlockHelper.placeSchematicBlock(ctx.getLevel(), to.getBlockState(), endpoint, ItemStack.EMPTY, secondData);
        SchematicConnectionData.restore(ctx.getLevel(), destination, firstData, mapping);
        SchematicConnectionData.restore(ctx.getLevel(), endpoint, secondData, mapping);
        var restoredFirst = (ChainConveyorBlockEntity)ctx.getLevel().getBlockEntity(destination);
        var restoredSecond = (ChainConveyorBlockEntity)ctx.getLevel().getBlockEntity(endpoint);
        ctx.assertTrue(restoredFirst.connections.contains(new BlockPos(2, 0, 0)), "First chain endpoint was lost");
        ctx.assertTrue(restoredSecond.connections.contains(new BlockPos(-2, 0, 0)), "Second chain endpoint was lost");
        ctx.succeed();
    }

    // Restore controller and indices after Create's movement warning clears native belt references
    @GameTest(template = "empty_container")
    public static void beltsKeepTheirControllerAfterSafePlacement(GameTestHelper ctx){
        BlockPos first = new BlockPos(1, 1, 1), second = new BlockPos(2, 1, 1);
        ctx.setBlock(first, state("belt")); ctx.setBlock(second, state("belt"));
        var from = (BeltBlockEntity)ctx.getBlockEntity(first);
        var to = (BeltBlockEntity)ctx.getBlockEntity(second);
        from.setController(from.getBlockPos()); to.setController(from.getBlockPos());
        from.beltLength = 2; to.beltLength = 2; from.index = 0; to.index = 1;
        BlockPos destination = ctx.absolutePos(new BlockPos(1, 1, 3));
        UUID source = UUID.randomUUID(), template = UUID.randomUUID();
        var frames = List.of(new SchematicConnectionData.BodyFrame(source, template, from.getBlockPos(), new BlockPos(2, 1, 1)));
        CompoundTag firstData = capture(ctx, from, source, frames), secondData = capture(ctx, to, source, frames);
        var mapping = mapping(template, destination);
        BlockPos endpoint = destination.offset(1, 0, 0);
        BlockHelper.placeSchematicBlock(ctx.getLevel(), from.getBlockState(), destination, ItemStack.EMPTY, firstData);
        BlockHelper.placeSchematicBlock(ctx.getLevel(), to.getBlockState(), endpoint, ItemStack.EMPTY, secondData);
        SchematicConnectionData.restore(ctx.getLevel(), destination, firstData, mapping);
        SchematicConnectionData.restore(ctx.getLevel(), endpoint, secondData, mapping);
        var restored = (BeltBlockEntity)ctx.getLevel().getBlockEntity(endpoint);
        ctx.assertTrue(destination.equals(restored.getController()), "Belt controller points to the old location");
        ctx.assertTrue(restored.beltLength == 2 && restored.index == 1, "Belt length or index was lost");
        ctx.succeed();
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Use the same safe callbacks and endpoint descriptors as the archive exporter
    private static CompoundTag capture(GameTestHelper ctx, BlockEntity be, UUID source,
                                       List<SchematicConnectionData.BodyFrame> frames){
        CompoundTag data = SchematicBlockData.safe(ctx.getLevel(), be.getBlockState(), be);
        SchematicConnectionData.capture(ctx.getLevel(), be, be.saveWithFullMetadata(ctx.getLevel().registryAccess()), data, source, frames);
        return data;
    }

    // Assign fresh destination coordinates without depending on a source body's old plot allocation
    private static SubLevelSchematicSerializationContext mapping(UUID template, BlockPos destination){
        var ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null);
        ctx.getMappings().put(template, new SubLevelSchematicSerializationContext.SchematicMapping(null, null,
                UUID.randomUUID(), pos -> ((BlockPos)pos).offset(destination)));
        return ctx;
    }

    // Resolve registered Create blocks without depending on its registration implementation
    private static BlockState state(String path){
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path)).defaultBlockState();
    }
}
