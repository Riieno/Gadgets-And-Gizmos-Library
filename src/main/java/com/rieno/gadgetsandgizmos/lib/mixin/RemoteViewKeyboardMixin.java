package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reserve Escape and either Control key for releasing remote camera control
@Mixin(KeyboardHandler.class)
public abstract class RemoteViewKeyboardMixin{
    // Release the session before vanilla opens a pause screen
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void controlLens(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci){
        if(!RemoteViewClient.isActive()) return;
        if(action == GLFW.GLFW_PRESS && (key == GLFW.GLFW_KEY_ESCAPE
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL)) RemoteViewClient.exit();
        ci.cancel();
    }
}
