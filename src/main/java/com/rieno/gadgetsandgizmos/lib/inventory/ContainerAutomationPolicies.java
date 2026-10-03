package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Enforce library automation locks independently of any consuming addon
public final class ContainerAutomationPolicies implements ContainerAccessRegistry.Provider{
    public static void register(){ ContainerAccessRegistry.register(new ContainerAutomationPolicies()); }

    private static ContainerAutomation config(Level level, BlockPos pos){
        if(level == null || level.isClientSide) return null;
        var root = SableLevelApi.serverLevel(level);
        BlockPos key = ContainerStorageIdentity.position(level, pos);
        return root == null ? null : ContainerAutomationStore.get(root).find(SableLevelApi.containingId(level, key), key);
    }

    @Override public boolean canOpen(ServerPlayer player, Level level, BlockPos pos){
        var config = config(level, pos);
        return config == null || !config.locked() || player.hasPermissions(2) || player.getUUID().equals(config.owner());
    }

    @Override public boolean canInsert(Level level, BlockPos pos, ItemStack stack){
        var config = config(level, pos);
        return config == null || config.accepts(level, stack);
    }

    @Override public boolean canExtract(Level level, BlockPos pos, ItemStack stack){
        var config = config(level, pos);
        return config == null || !config.locked() || config.push() && config.accepts(level, stack);
    }
}
