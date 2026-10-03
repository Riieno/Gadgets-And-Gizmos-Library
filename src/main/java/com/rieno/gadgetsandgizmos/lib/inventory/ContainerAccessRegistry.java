package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.rieno.gadgetsandgizmos.lib.mixin.CompoundContainerAccess;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Enforce dynamic container policies even through already cached item handlers
public final class ContainerAccessRegistry{
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private ContainerAccessRegistry(){}

    public static void register(Provider provider){
        if(provider == null) throw new IllegalArgumentException("Container policy provider is missing");
        PROVIDERS.add(provider);
    }

    public static boolean canOpen(ServerPlayer player, Level level, BlockPos pos){
        for(Provider provider : PROVIDERS) if(!provider.canOpen(player, level, pos)) return false;
        return true;
    }

    public static boolean canOpen(ServerPlayer player, Container container){
        if(container instanceof BlockEntity be) return canOpen(player, be.getLevel(), be.getBlockPos());
        if(container instanceof CompoundContainerAccess pair) return canOpen(player, pair.gadgetsngizmos$first()) && canOpen(player, pair.gadgetsngizmos$second());
        return true;
    }

    public static boolean canOpen(ServerPlayer player, IItemHandler handler){
        return !(handler instanceof GuardedHandler guarded) || canOpen(player, guarded.level(), guarded.pos());
    }

    public static boolean canTransfer(Container container, ItemStack stack, boolean insert){
        if(container instanceof BlockEntity be) return insert ? canInsert(be.getLevel(), be.getBlockPos(), stack) : canExtract(be.getLevel(), be.getBlockPos(), stack);
        if(container instanceof CompoundContainerAccess pair) return canTransfer(pair.gadgetsngizmos$first(), stack, insert) && canTransfer(pair.gadgetsngizmos$second(), stack, insert);
        return true;
    }

    public static boolean canInsert(Level level, BlockPos pos, ItemStack stack){
        for(Provider provider : PROVIDERS) if(!provider.canInsert(level, pos, stack)) return false;
        return true;
    }

    public static boolean canExtract(Level level, BlockPos pos, ItemStack stack){
        for(Provider provider : PROVIDERS) if(!provider.canExtract(level, pos, stack)) return false;
        return true;
    }

    // Query current policies on every mutation rather than caching permission decisions
    public static IItemHandler wrap(Level level, BlockPos pos, IItemHandler handler){
        if(handler instanceof GuardedHandler) return handler;
        return new GuardedHandler(level, ContainerStorageIdentity.position(level, pos).immutable(), handler);
    }

    public static boolean sameStorage(IItemHandler first, IItemHandler second){
        if(first == second) return true;
        return first instanceof GuardedHandler a && second instanceof GuardedHandler b && a.level() == b.level() && a.pos().equals(b.pos());
    }

    public interface Provider{
        boolean canOpen(ServerPlayer player, Level level, BlockPos pos);
        boolean canInsert(Level level, BlockPos pos, ItemStack stack);
        boolean canExtract(Level level, BlockPos pos, ItemStack stack);
    }

    private record GuardedHandler(Level level, BlockPos pos, IItemHandler delegate) implements IItemHandler{
        @Override public int getSlots(){ return delegate.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot){ return delegate.getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot){ return delegate.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack){ return canInsert(level, pos, stack) && delegate.isItemValid(slot, stack); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate){
            return canInsert(level, pos, stack) ? delegate.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate){
            return canExtract(level, pos, delegate.getStackInSlot(slot)) ? delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    }
}
