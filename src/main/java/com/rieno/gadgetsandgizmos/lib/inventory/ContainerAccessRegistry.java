package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

// Retain source compatibility after retiring global container access policies
@Deprecated
public final class ContainerAccessRegistry{
    private ContainerAccessRegistry(){}

    // Accept former registrations without retaining or applying their policies
    public static void register(Provider provider){
        if(provider == null) throw new IllegalArgumentException("Container policy provider is missing");
    }

    public static boolean canOpen(ServerPlayer player, Level level, BlockPos pos){ return true; }
    public static boolean canOpen(ServerPlayer player, Container container){ return true; }
    public static boolean canOpen(ServerPlayer player, IItemHandler handler){ return true; }
    public static boolean canTransfer(Container container, ItemStack stack, boolean insert){ return true; }
    public static boolean canInsert(Level level, BlockPos pos, ItemStack stack){ return true; }
    public static boolean canExtract(Level level, BlockPos pos, ItemStack stack){ return true; }

    // Return the original capability without replacing its type or identity
    public static IItemHandler wrap(Level level, BlockPos pos, IItemHandler handler){ return handler; }
    public static IItemHandler unwrap(IItemHandler handler){ return handler; }
    public static boolean sameStorage(IItemHandler first, IItemHandler second){ return first == second; }

    public interface Provider{
        boolean canOpen(ServerPlayer player, Level level, BlockPos pos);
        boolean canInsert(Level level, BlockPos pos, ItemStack stack);
        boolean canExtract(Level level, BlockPos pos, ItemStack stack);
    }
}
