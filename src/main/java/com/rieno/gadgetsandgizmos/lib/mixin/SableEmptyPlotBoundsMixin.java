package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Retain Sable's native empty state until a plot has occupied bounds
@Mixin(value = LevelPlot.class, remap = false)
public abstract class SableEmptyPlotBoundsMixin{
    // Store no local box while the plot is empty
    @Shadow protected BoundingBox3i localBounds;

    // Avoid normalizing empty corners when applying a tracking or bounds update
    @Inject(method = "setBoundingBox", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$retainEmptyBounds(BoundingBox3ic bounds, CallbackInfo ci){
        if(bounds.minX() <= bounds.maxX() && bounds.minY() <= bounds.maxY() && bounds.minZ() <= bounds.maxZ()) return;
        localBounds = null;
        ci.cancel();
    }
}
