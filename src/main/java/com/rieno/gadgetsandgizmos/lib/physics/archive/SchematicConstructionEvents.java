package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

// Protect reserved schematic materials from removal while their construction can still be refunded
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class SchematicConstructionEvents{
    private SchematicConstructionEvents(){}

    // Reject mining partial structures before their materials are committed
    @SubscribeEvent public static void onBreak(BlockEvent.BreakEvent evt){
        if(evt.getLevel() instanceof Level level && building(level, evt.getPos())) evt.setCanceled(true);
    }

    // Reject added blocks that would be lost during construction rollback
    @SubscribeEvent public static void onPlace(BlockEvent.EntityPlaceEvent evt){
        if(evt.getLevel() instanceof Level level && building(level, evt.getPos())) evt.setCanceled(true);
    }

    // Keep containers and machinery inaccessible until the completed assembly is released
    @SubscribeEvent public static void onInteract(PlayerInteractEvent.RightClickBlock evt){
        if(building(evt.getLevel(), evt.getPos())) evt.setCanceled(true);
    }

    // Exclude unfinished plot blocks from explosion drops and destruction
    @SubscribeEvent public static void onExplosion(ExplosionEvent.Detonate evt){
        evt.getAffectedBlocks().removeIf(pos -> building(evt.getLevel(), pos));
    }

    // Resolve native plot ownership without loading terrain or requiring addon classes
    private static boolean building(Level level, BlockPos pos){
        if(level.isClientSide) return false;
        var body = Sable.HELPER.getContaining(level, pos);
        return body instanceof ServerSubLevel serverBody && SubLevelConstructionState.isBuilding(serverBody);
    }
}
