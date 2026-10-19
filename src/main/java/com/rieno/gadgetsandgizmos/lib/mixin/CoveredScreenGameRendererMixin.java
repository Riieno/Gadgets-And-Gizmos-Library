package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.render.WorldRenderControl;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Leave screen rendering and client ticks running while an opaque editor covers the world
@Mixin(GameRenderer.class)
public abstract class CoveredScreenGameRendererMixin{
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean coveredScreenWorldRendering(boolean requested){
        return ViewSceneRenderer.isCapturing() ? requested
                : WorldRenderControl.shouldRenderWorld(requested, Minecraft.getInstance().screen);
    }
}
