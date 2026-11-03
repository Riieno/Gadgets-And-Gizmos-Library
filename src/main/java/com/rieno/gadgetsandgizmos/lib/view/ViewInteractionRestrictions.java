package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

// Limit authenticated camera use to existing block controls and menus
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class ViewInteractionRestrictions{
    // Keep interaction policy on the static facade
    private ViewInteractionRestrictions(){}

    // Include native replay scopes as well as full remote-view sessions
    public static boolean isRestricted(Player player){
        return player instanceof ServerPlayer server && RemoteViewSessions.isLocked(server)
                || ViewInteractionContext.perspective(player) != null;
    }

    // Skip held-item actions while allowing the selected block's own interaction
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void useBlock(PlayerInteractEvent.RightClickBlock evt){
        if(!isRestricted(evt.getEntity())) return;
        evt.setUseItem(TriState.FALSE);
    }

    // Reject placement even when a native handler attempts it during camera use
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void placeBlock(BlockEvent.EntityPlaceEvent evt){
        if(evt.getEntity() instanceof Player player && isRestricted(player)) evt.setCanceled(true);
    }

    // Reject block destruction on the authoritative server
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breakBlock(BlockEvent.BreakEvent evt){
        if(isRestricted(evt.getPlayer())) evt.setCanceled(true);
    }
}
