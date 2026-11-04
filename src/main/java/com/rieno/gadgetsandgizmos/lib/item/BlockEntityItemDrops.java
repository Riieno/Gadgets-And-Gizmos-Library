package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

// Carry host-supplied block entity data through ordinary item drops
public final class BlockEntityItemDrops{
    private BlockEntityItemDrops(){}

    // Copy portable data without retaining the source block position
    public static ItemStack create(BlockEntity blockEntity, CompoundTag data){
        ItemStack res = new ItemStack(blockEntity.getBlockState().getBlock());
        if(res.isEmpty()) return res;
        CompoundTag portable = data.copy();
        portable.remove("x");
        portable.remove("y");
        portable.remove("z");
        BlockItem.setBlockEntityData(res, blockEntity.getType(), portable);
        res.applyComponents(blockEntity.collectComponents());
        return res;
    }

    // Replace matching loot while preserving counts and unrelated drops
    public static List<ItemStack> replace(List<ItemStack> drops, ItemStack retained){
        List<ItemStack> res = new ArrayList<>(drops.size());
        for(ItemStack stack : drops){
            res.add(!retained.isEmpty() && stack.is(retained.getItem())
                    ? retained.copyWithCount(stack.getCount()) : stack);
        }
        return res;
    }
}
