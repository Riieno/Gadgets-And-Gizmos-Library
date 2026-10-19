package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Retain the retired policy entry point without registering container restrictions
@Deprecated
public final class ContainerAutomationPolicies implements ContainerAccessRegistry.Provider{
    public static void register(){}
    @Override public boolean canOpen(ServerPlayer player, Level level, BlockPos pos){ return true; }
    @Override public boolean canInsert(Level level, BlockPos pos, ItemStack stack){ return true; }
    @Override public boolean canExtract(Level level, BlockPos pos, ItemStack stack){ return true; }
}
