package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Route raw mouse input into the active lens controls
@Mixin(MouseHandler.class)
public abstract class RemoteViewMouseMixin{
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    // Forward motion without changing the player's body rotation
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void turnLens(double movementTime, CallbackInfo ci){
        if(!RemoteViewClient.isActive()) return;
        RemoteViewClient.turn(accumulatedDX, accumulatedDY);
        ci.cancel();
    }
    // Forward scroll without selecting another hotbar item
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void zoomLens(long window, double horizontal, double vertical, CallbackInfo ci){
        if(!RemoteViewClient.isActive()) return;
        RemoteViewClient.zoom(vertical);
        ci.cancel();
    }
    // Suppress attack and use presses while allowing held buttons to release
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void suppressButtons(long window, int button, int action, int modifiers, CallbackInfo ci){
        if(RemoteViewClient.isActive() && action != GLFW.GLFW_RELEASE) ci.cancel();
    }
}
