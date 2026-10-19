package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep secondary terrain allocation from destroying the player's Flywheel visuals and uniform buffers
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.FlwImplXplatImpl", remap = false)
public abstract class ViewSceneFlywheelReloadMixin{
    // Only the player's renderer may issue a world visualization reload
    @Inject(method = "dispatchReloadLevelRendererEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void retainPlayerResources(ClientLevel level, CallbackInfo ci){
        if(ViewSceneRenderer.isCapturing()) ci.cancel();
    }
}
