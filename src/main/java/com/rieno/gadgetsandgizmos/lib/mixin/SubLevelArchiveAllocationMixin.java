package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelArchiveStore;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Reserve persistent archive slots before Sable searches for a new plot
@Mixin(SubLevelContainer.class)
public abstract class SubLevelArchiveAllocationMixin{
    @Inject(method = "allocateNewSubLevel", at = @At("HEAD"))
    private void gadgetsngizmos$reserveArchives(Pose3d pose, CallbackInfoReturnable<SubLevel> cir){
        if((Object) this instanceof ServerSubLevelContainer container) SubLevelArchiveStore.reservePlots(container);
    }
}
