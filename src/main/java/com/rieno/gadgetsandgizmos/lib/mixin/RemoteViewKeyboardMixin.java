package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient;
import com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewKeys;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Route configured lens actions before suppressing ordinary player controls
@Mixin(KeyboardHandler.class)
public abstract class RemoteViewKeyboardMixin{
    // Release the session before vanilla opens a pause screen
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void controlLens(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci){
        if(!RemoteViewClient.isActive() || net.minecraft.client.Minecraft.getInstance().screen != null) return;
        if(!RemoteViewKeys.keyboard(key, scancode, action))
            com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient.key(key, scancode, action, modifiers);
        ci.cancel();
    }
}
