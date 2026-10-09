package com.rieno.gadgetsandgizmos.lib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewCameraAccess;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep body FOV, bobbing and held items out of camera lens rendering
@Mixin(GameRenderer.class)
public abstract class RemoteViewGameRendererMixin{
    // Expose the secondary camera to world render integrations
    @Inject(method = "getMainCamera", at = @At("HEAD"), cancellable = true)
    private void captureCamera(CallbackInfoReturnable<Camera> cir){
        Camera camera = ViewSceneRenderer.captureCamera();
        if(camera != null) cir.setReturnValue(camera);
    }
    // Use the lens field of view and finish applying its stabilised pose
    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void lensFov(Camera camera, float partialTick, boolean useSetting, CallbackInfoReturnable<Double> cir){
        ViewPose pose = ViewSceneRenderer.capturePose();
        if(pose == null) pose = RemoteViewClient.pose(partialTick);
        if(pose == null) return;
        ((ViewCameraAccess) camera).gadgetsngizmos$applyView(pose);
        cir.setReturnValue(pose.fov());
    }
    // Suppress player damage and walking motion in the lens projection
    @Inject(method = {"bobHurt", "bobView"}, at = @At("HEAD"), cancellable = true)
    private void stableLens(PoseStack stack, float partialTick, CallbackInfo ci){
        if(RemoteViewClient.isActive() || ViewSceneRenderer.isCapturing()) ci.cancel();
    }
    // Hide the held item while the player sees a remote lens
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void hideHand(Camera camera, float partialTick, Matrix4f matrix, CallbackInfo ci){
        if(RemoteViewClient.isActive()) ci.cancel();
    }
    // Preserve the body interaction target while the remote view is active
    @Inject(method = "pick(F)V", at = @At("HEAD"), cancellable = true)
    private void suppressPick(float partialTick, CallbackInfo ci){
        if(RemoteViewClient.isActive()) ci.cancel();
    }
}
