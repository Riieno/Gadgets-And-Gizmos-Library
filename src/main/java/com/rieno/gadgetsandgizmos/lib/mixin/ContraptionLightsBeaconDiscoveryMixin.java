package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Discover moving beacons through loaded plot contents instead of a bounds-sized chunk scan
@Pseudo
@Mixin(targets = "xyz.atmerek.contraptionlights.sable.SubLevelBeaconBeams", remap = false)
public abstract class ContraptionLightsBeaconDiscoveryMixin{
    // Preserve beacon filtering and let Contraption Lights retain its beam cache and updates
    @Inject(method = "discover(Ldev/ryanhcode/sable/sublevel/ClientSubLevel;Ldev/ryanhcode/sable/sublevel/plot/LevelPlot;)[J",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void gadgetsngizmos$loadedBeacons(ClientSubLevel body, LevelPlot plot,
            CallbackInfoReturnable<long[]> cir){
        var found = new LongArrayList();
        var bounds = plot.getBoundingBox();
        for(var beacon : SubLevelBlockEntityCollector.getBlockEntities(body, BeaconBlockEntity.class)){
            var pos = beacon.getBlockPos();
            if(!beacon.isRemoved() && bounds.contains(pos.getX(), pos.getY(), pos.getZ())) found.add(pos.asLong());
        }
        cir.setReturnValue(found.toLongArray());
    }
}
