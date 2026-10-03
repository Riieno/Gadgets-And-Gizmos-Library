package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Run generic container automation and protect direct player access to locked containers
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID)
public final class ContainerAutomationEvents{
    private ContainerAutomationEvents(){}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event){
        for(var level : event.getServer().getAllLevels()){
            ContainerAutomationRuntime.tick(level);
            if(level.getGameTime() % 100 == 0) com.rieno.gadgetsandgizmos.lib.physics.SubLevelLocator.get(level).refresh(level);
        }
    }

    @SubscribeEvent
    public static void use(PlayerInteractEvent.RightClickBlock event){
        if(event.getEntity() instanceof ServerPlayer player && !ContainerAccessRegistry.canOpen(player, event.getLevel(), event.getPos())){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breakBlock(BlockEvent.BreakEvent event){
        if(!(event.getPlayer() instanceof ServerPlayer player)) return;
        if(!ContainerAccessRegistry.canOpen(player, player.serverLevel(), event.getPos())) event.setCanceled(true);
        else if(!event.isCanceled()){
            var pos = ContainerStorageIdentity.position(player.level(), event.getPos());
            ContainerAutomationStore.get(player.serverLevel()).remove(SableLevelApi.containingId(player.level(), pos), pos);
        }
    }
}
