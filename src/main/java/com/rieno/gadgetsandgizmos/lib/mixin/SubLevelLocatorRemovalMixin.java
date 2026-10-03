package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.SubLevelLocator;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Retain unloaded locations but discard bodies removed from the world
@Mixin(ServerSubLevelContainer.class)
public abstract class SubLevelLocatorRemovalMixin{
    @Inject(method = "removeSubLevel", at = @At("HEAD"), remap = false)
    private void gadgetsngizmos$removeLocation(int x, int z, SubLevelRemovalReason reason, CallbackInfo ci){
        if(reason != SubLevelRemovalReason.REMOVED) return;
        var container = (ServerSubLevelContainer) (Object) this;
        var body = container.getSubLevel(x, z);
        if(body != null) SubLevelLocator.get(container.getLevel()).remove(body.getUniqueId());
    }
}
