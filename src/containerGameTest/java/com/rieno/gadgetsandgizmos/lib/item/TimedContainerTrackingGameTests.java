package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class TimedContainerTrackingGameTests{
    @GameTest(template = "empty_container")
    public static void recipeProbeAcceptsNullLevel(GameTestHelper ctx){
        ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        ItemStack contents = new ItemStack(Items.BLACKSTONE);
        chest.setItem(0, contents);
        chest.setLevel(null);
        ctx.assertTrue(chest.getLevel() == null, "Recipe probe gained a world");
        ctx.assertTrue(chest.getItem(0) == contents, "Tracking changed the probe inventory");
        ctx.succeed();
    }

    @GameTest(template = "empty_container")
    public static void placedContainerCanDetachAndReattach(GameTestHelper ctx){
        BlockPos pos = new BlockPos(1, 1, 1);
        ctx.setBlock(pos, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity chest = (ChestBlockEntity)ctx.getBlockEntity(pos);
        ItemStack contents = new ItemStack(Items.COAL, 2);
        chest.setItem(0, contents);
        chest.setLevel(null);
        ctx.assertTrue(chest.getLevel() == null, "Detached chest retained its world");
        chest.setLevel(ctx.getLevel());
        ctx.assertTrue(chest.getLevel() == ctx.getLevel(), "Chest did not reattach");
        ctx.assertTrue(chest.getItem(0) == contents, "Reattaching changed stored items");
        ctx.succeed();
    }
}
