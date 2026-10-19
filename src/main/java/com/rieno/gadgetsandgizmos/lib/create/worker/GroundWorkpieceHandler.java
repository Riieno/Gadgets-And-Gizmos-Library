package com.rieno.gadgetsandgizmos.lib.create.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

// An explicitly linked press can use the loose items directly beneath it.
final class GroundWorkpieceHandler implements IItemHandler {
    private final Level level;
    private final BlockPos pos;

    GroundWorkpieceHandler(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    private List<ItemEntity> items() {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos),
                entity -> entity.isAlive() && !entity.getItem().isEmpty());
    }

    @Override public int getSlots() { return 1; }

    @Override public ItemStack getStackInSlot(int slot) {
        if(slot != 0) return ItemStack.EMPTY;
        List<ItemEntity> items = items();
        return items.isEmpty() ? ItemStack.EMPTY : items.getFirst().getItem().copy();
    }

    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if(slot != 0 || stack.isEmpty() || level.isClientSide) return stack;
        int amount = Math.min(stack.getCount(), stack.getMaxStackSize());
        if(!simulate) {
            ItemEntity item = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.05D,
                    pos.getZ() + 0.5D, stack.copyWithCount(amount));
            item.setDeltaMovement(0, 0, 0);
            if(!level.addFreshEntity(item)) return stack;
        }
        return stack.getCount() == amount ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - amount);
    }

    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if(slot != 0 || amount <= 0) return ItemStack.EMPTY;
        List<ItemEntity> items = items();
        if(items.isEmpty()) return ItemStack.EMPTY;
        ItemEntity entity = items.getFirst();
        ItemStack held = entity.getItem();
        ItemStack extracted = held.copyWithCount(Math.min(held.getCount(), amount));
        if(!simulate) {
            if(extracted.getCount() == held.getCount()) entity.discard();
            else entity.setItem(held.copyWithCount(held.getCount() - extracted.getCount()));
        }
        return extracted;
    }

    @Override public int getSlotLimit(int slot) { return slot == 0 ? 64 : 0; }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0; }
}
