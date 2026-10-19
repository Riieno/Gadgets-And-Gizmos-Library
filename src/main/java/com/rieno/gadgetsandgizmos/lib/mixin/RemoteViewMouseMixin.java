package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient;
import com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewKeys;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Route raw mouse input into the active lens controls
@Mixin(MouseHandler.class)
public abstract class RemoteViewMouseMixin{
    // Existing drag hooks consume motion first; unclaimed motion turns the lens
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void turnLens(LocalPlayer player, double yaw, double pitch, Operation<Void> original){
        if(RemoteViewClient.isActive() && net.minecraft.client.Minecraft.getInstance().screen == null) RemoteViewClient.turnScaled(yaw, pitch);
        else original.call(player, yaw, pitch);
    }
    // Forward scroll without selecting another hotbar item
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void zoomLens(long window, double horizontal, double vertical, CallbackInfo ci){
        if(!RemoteViewClient.isActive() || net.minecraft.client.Minecraft.getInstance().screen != null) return;
        if(!ViewInteractionClient.scrolling(horizontal, vertical)) RemoteViewClient.zoom(vertical);
        ci.cancel();
    }
    // Match configurable mouse actions without attacking or using held items
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void suppressButtons(long window, int button, int action, int modifiers, CallbackInfo ci){
        if(!RemoteViewClient.isActive() || net.minecraft.client.Minecraft.getInstance().screen != null) return;
        if(!RemoteViewKeys.mouse(button, action)) ViewInteractionClient.mouse(button, action, modifiers);
        ci.cancel();
    }
}
