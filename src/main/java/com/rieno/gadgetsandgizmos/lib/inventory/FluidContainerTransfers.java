package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

// Exchange one held container, returning surplus containers to the player without discarding items
public final class FluidContainerTransfers{
    private FluidContainerTransfers(){}

    // Choose the direction from the bucket and require room for its entire contents before inserting
    public static ItemStack transferBucket(Player player, ItemStack carried, IFluidHandler tank){
        if(carried.isEmpty() || !(carried.getItem() instanceof BucketItem)) return carried;
        var fluid = FluidUtil.getFluidContained(carried.copyWithCount(1));
        if(fluid.isEmpty()) return carried.is(Items.BUCKET) ? transfer(player, carried, tank, true) : carried;
        var contents = fluid.get();
        if(contents.getAmount() != FluidType.BUCKET_VOLUME
                || tank.fill(contents, IFluidHandler.FluidAction.SIMULATE) != contents.getAmount()) return carried;
        return transfer(player, carried, tank, false);
    }

    public static ItemStack transfer(Player player, ItemStack carried, IFluidHandler tank, boolean fillContainer){
        if(carried.isEmpty()) return carried;
        FluidActionResult result = fillContainer
                ? FluidUtil.tryFillContainer(carried, tank, 1000, player, true)
                : FluidUtil.tryEmptyContainer(carried, tank, 1000, player, true);
        if(!result.isSuccess()) return carried;
        if(carried.getCount() == 1) return result.getResult();
        ItemStack remainder = carried.copyWithCount(carried.getCount() - 1);
        ItemStack container = result.getResult();
        if(!container.isEmpty() && !player.getInventory().add(container)) player.drop(container, false);
        return remainder;
    }
}
