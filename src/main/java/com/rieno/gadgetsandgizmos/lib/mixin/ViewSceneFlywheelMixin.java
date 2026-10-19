package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Use block entity fallback rendering in feeds while Flywheel retains the player's visual state
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl", remap = false)
public abstract class ViewSceneFlywheelMixin{
    // Prevent secondary frustums and render origins from updating the shared visual manager
    @Inject(method = "supportsVisualization", at = @At("HEAD"), cancellable = true, remap = false)
    private static void retainPlayerVisuals(LevelAccessor level, CallbackInfoReturnable<Boolean> cir){
        if(ViewSceneRenderer.isCapturing()) cir.setReturnValue(false);
    }
}
